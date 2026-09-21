package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ScanListResponse;
import com.memeto.team.memeto3d.dto.ScanResponse;
import com.memeto.team.memeto3d.dto.UpdateScanRequest;
import com.memeto.team.memeto3d.entity.InputMode;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.entity.ScanStatus;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.exception.ScanConflictException;
import com.memeto.team.memeto3d.exception.StorageQuotaExceededException;
import com.memeto.team.memeto3d.repository.ScanRepository;
import com.memeto.team.memeto3d.repository.UserRepository;
import com.memeto.team.memeto3d.service.ScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanServiceImpl implements ScanService {

    private final ScanRepository scanRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ScanResponse createScan(CreateScanRequest request, UUID userId) {
        log.info("Creating new scan session '{}' for user ID: {}", request.getName(), userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        long storageUsed = user.getStorageUsedBytes() != null ? user.getStorageUsedBytes() : 0L;
        long storageQuota = user.getStorageQuotaBytes() != null ? user.getStorageQuotaBytes() : 10737418240L;

        // Check storage quota: users.storage_used_bytes < users.storage_quota_bytes
        if (storageUsed >= storageQuota) {
            log.warn("User {} exceeded storage quota. Used: {} bytes, Quota: {} bytes", userId, storageUsed, storageQuota);
            throw new StorageQuotaExceededException("Xoá scan cũ để tiếp tục");
        }

        InputMode mode = InputMode.fromString(request.getInputMode());

        Scan scan = Scan.builder()
                .user(user)
                .name(request.getName().trim())
                .description(request.getDescription())
                .notes(request.getNotes())
                .inputMode(mode)
                .status(ScanStatus.draft)
                .fileCount(0)
                .totalSizeBytes(0L)
                .build();

        Scan savedScan = scanRepository.saveAndFlush(scan);
        log.info("Successfully created scan session with ID: {} for user: {}", savedScan.getId(), userId);

        return ScanResponse.fromEntity(savedScan);
    }

    @Override
    @Transactional(readOnly = true)
    public ScanListResponse getScans(UUID userId, String status, int page, int limit, String sort) {
        log.info("Retrieving scans for user ID: {}, status: {}, page: {}, limit: {}, sort: {}",
                userId, status, page, limit, sort);

        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        if (page < 1) {
            throw new IllegalArgumentException("Page must be at least 1");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }

        ScanStatus filterStatus = null;
        if (status != null && !status.isBlank()) {
            filterStatus = ScanStatus.fromString(status);
        }

        Sort sortOrder = parseSort(sort);
        Pageable pageable = PageRequest.of(page - 1, limit, sortOrder);

        Page<Scan> scanPage;
        if (filterStatus != null) {
            scanPage = scanRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, filterStatus, pageable);
        } else {
            scanPage = scanRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
        }

        List<ScanResponse> scanResponses = scanPage.getContent().stream()
                .map(ScanResponse::fromEntity)
                .toList();

        return ScanListResponse.builder()
                .scans(scanResponses)
                .totalCount(scanPage.getTotalElements())
                .page(page)
                .limit(limit)
                .totalPages(scanPage.getTotalPages())
                .build();
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String trimmed = sort.trim();
        String[] parts;
        if (trimmed.contains(",")) {
            parts = trimmed.split(",", 2);
        } else if (trimmed.contains(":")) {
            parts = trimmed.split(":", 2);
        } else if (trimmed.contains(" ")) {
            parts = trimmed.split("\\s+", 2);
        } else {
            parts = new String[]{trimmed};
        }

        String field = parts[0].trim().toLowerCase();
        String targetProperty;
        switch (field) {
            case "created_at":
            case "createdat":
                targetProperty = "createdAt";
                break;
            case "updated_at":
            case "updatedat":
                targetProperty = "updatedAt";
                break;
            case "name":
                targetProperty = "name";
                break;
            case "status":
                targetProperty = "status";
                break;
            case "file_count":
            case "filecount":
                targetProperty = "fileCount";
                break;
            case "total_size_bytes":
            case "totalsizebytes":
                targetProperty = "totalSizeBytes";
                break;
            default:
                throw new IllegalArgumentException("Invalid sort field: " + parts[0].trim()
                        + ". Allowed fields are: created_at, updated_at, name, status, file_count, total_size_bytes");
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1) {
            String dirStr = parts[1].trim();
            if ("asc".equalsIgnoreCase(dirStr)) {
                direction = Sort.Direction.ASC;
            } else if ("desc".equalsIgnoreCase(dirStr)) {
                direction = Sort.Direction.DESC;
            } else {
                throw new IllegalArgumentException("Invalid sort direction: " + dirStr + ". Must be 'asc' or 'desc'");
            }
        }

        return Sort.by(direction, targetProperty);
    }

    @Override
    @Transactional(readOnly = true)
    public ScanResponse getScanById(UUID id, UUID currentUserId, Role currentUserRole) {
        log.info("Fetching scan session details for ID: {}, requested by user ID: {} with role: {}", id, currentUserId, currentUserRole);

        Scan scan = scanRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found with ID: " + id));

        boolean isPrivileged = currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_OPERATOR;
        if (!isPrivileged && (scan.getUser() == null || !scan.getUser().getId().equals(currentUserId))) {
            log.warn("Access denied for user {} to scan {} owned by {}",
                    currentUserId, id, scan.getUser() != null ? scan.getUser().getId() : null);
            throw new ResourceNotFoundException("Scan not found with ID: " + id);
        }

        return ScanResponse.fromEntity(scan);
    }

    @Override
    @Transactional
    public ScanResponse updateScan(UUID id, UpdateScanRequest request, UUID currentUserId, Role currentUserRole) {
        log.info("Updating scan session for ID: {}, requested by user ID: {} with role: {}", id, currentUserId, currentUserRole);

        Scan scan = scanRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found with ID: " + id));

        boolean isPrivileged = currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_OPERATOR;
        if (!isPrivileged && (scan.getUser() == null || !scan.getUser().getId().equals(currentUserId))) {
            log.warn("Access denied for user {} to update scan {} owned by {}",
                    currentUserId, id, scan.getUser() != null ? scan.getUser().getId() : null);
            throw new ResourceNotFoundException("Scan not found with ID: " + id);
        }

        if (scan.getStatus() == ScanStatus.uploading || scan.getStatus() == ScanStatus.processing) {
            log.warn("Cannot update scan {} with status {}", id, scan.getStatus());
            throw new ScanConflictException("Cannot update scan while it is in " + scan.getStatus() + " status");
        }

        if (request != null) {
            if (request.getName() != null) {
                String trimmedName = request.getName().trim();
                if (trimmedName.isEmpty()) {
                    throw new IllegalArgumentException("Scan name cannot be empty");
                }
                if (trimmedName.length() > 255) {
                    throw new IllegalArgumentException("Name must not exceed 255 characters");
                }
                scan.setName(trimmedName);
            }
            if (request.getDescription() != null) {
                scan.setDescription(request.getDescription().trim());
            }
            if (request.getNotes() != null) {
                scan.setNotes(request.getNotes().trim());
            }
        }

        scan.setUpdatedAt(Instant.now());
        Scan updatedScan = scanRepository.saveAndFlush(scan);
        log.info("Successfully updated scan session with ID: {}", id);

        return ScanResponse.fromEntity(updatedScan);
    }
}

