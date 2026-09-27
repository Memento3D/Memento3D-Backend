package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ScanImageListResponse;
import com.memeto.team.memeto3d.dto.ScanImageResponse;
import com.memeto.team.memeto3d.dto.ScanImageUploadResponse;
import com.memeto.team.memeto3d.dto.ScanListResponse;
import com.memeto.team.memeto3d.dto.ScanResponse;
import com.memeto.team.memeto3d.dto.UpdateScanRequest;
import com.memeto.team.memeto3d.entity.InputMode;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.entity.ScanImage;
import com.memeto.team.memeto3d.entity.ScanImageUploadStatus;
import com.memeto.team.memeto3d.entity.ScanStatus;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.FileSizeExceededException;
import com.memeto.team.memeto3d.exception.InvalidFileFormatException;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.exception.ScanConflictException;
import com.memeto.team.memeto3d.exception.StorageQuotaExceededException;
import org.springframework.jdbc.core.JdbcTemplate;
import com.memeto.team.memeto3d.repository.ScanImageRepository;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanServiceImpl implements ScanService {

    // ---- constants ----
    /** Maximum allowed image size per FR-030 (20 MB). */
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;

    /** Accepted MIME types — mirrors FR-028 (jpg/jpeg/png). */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png");

    /** Accepted file extensions (lowercased). */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    // ---- repositories ----
    private final ScanRepository scanRepository;
    private final UserRepository userRepository;
    private final ScanImageRepository scanImageRepository;
    private final com.memeto.team.memeto3d.repository.ScanVideoRepository scanVideoRepository;
    /** Used to check whether a quality_check_run exists for a scan without requiring a full JPA entity. */
    private final JdbcTemplate jdbcTemplate;

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

    @Override
    @Transactional
    public void deleteScan(UUID id, UUID currentUserId, Role currentUserRole) {
        log.info("Soft-deleting scan session for ID: {}, requested by user ID: {} with role: {}", id, currentUserId, currentUserRole);

        Scan scan = scanRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found with ID: " + id));

        // IDOR protection: non-privileged users can only delete their own scans
        boolean isPrivileged = currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_OPERATOR;
        if (!isPrivileged && (scan.getUser() == null || !scan.getUser().getId().equals(currentUserId))) {
            log.warn("Access denied for user {} to delete scan {} owned by {}",
                    currentUserId, id, scan.getUser() != null ? scan.getUser().getId() : null);
            throw new ResourceNotFoundException("Scan not found with ID: " + id);
        }

        // Block deletion while a processing job is running
        if (scan.getStatus() == ScanStatus.processing) {
            log.warn("Cannot delete scan {} because it is currently in 'processing' status", id);
            throw new ScanConflictException("Cannot delete scan while it is in 'processing' status. Wait for processing to complete or fail.");
        }

        // Soft-delete: set status and deleted_at timestamp
        // NOTE: storage_used_bytes is intentionally NOT decremented here.
        // Per FR-177, quota will only be freed by the maintenance job after the 90-day hard delete.
        // This prevents users from gaming the quota by cycling delete/create operations.
        scan.setStatus(ScanStatus.deleted);
        scan.setDeletedAt(Instant.now());
        scanRepository.saveAndFlush(scan);

        log.info("Successfully soft-deleted scan session with ID: {} by user: {}", id, currentUserId);
    }

    // =========================================================================
    // BE1 — GET /api/v1/scans/{id}/images
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public ScanImageListResponse getScanImages(UUID scanId, UUID currentUserId, Role currentUserRole,
                                               int page, int limit) {
        log.info("Fetching images for scan ID: {}, user: {}, role: {}, page: {}, limit: {}",
                scanId, currentUserId, currentUserRole, page, limit);

        if (page < 1) throw new IllegalArgumentException("Page must be at least 1");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Limit must be between 1 and 100");

        Scan scan = scanRepository.findByIdAndDeletedAtIsNull(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found with ID: " + scanId));

        enforceOwnership(scan, currentUserId, currentUserRole, scanId);

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.unsorted());
        Page<ScanImage> imagePage = scanImageRepository.findByScanIdAndDeletedAtIsNull(scanId, pageable);

        List<ScanImageResponse> imageResponses = imagePage.getContent().stream()
                .map(ScanImageResponse::fromEntity)
                .toList();

        return ScanImageListResponse.builder()
                .images(imageResponses)
                .totalCount(imagePage.getTotalElements())
                .page(page)
                .limit(limit)
                .totalPages(imagePage.getTotalPages())
                .build();
    }

    // =========================================================================
    // BE2 — POST /api/v1/scans/{id}/images
    // =========================================================================

    @Override
    @Transactional
    public ScanImageUploadResponse addImageToScan(UUID scanId, MultipartFile file,
                                                  UUID currentUserId, Role currentUserRole) {
        log.info("Adding image '{}' to scan ID: {}, requested by user: {}",
                file != null ? file.getOriginalFilename() : "null", scanId, currentUserId);

        // 1. Null-guard on file
        if (file == null || file.isEmpty()) {
            throw new InvalidFileFormatException("No file provided or file is empty");
        }

        // 2. Verify scan exists and is not deleted
        Scan scan = scanRepository.findByIdAndDeletedAtIsNull(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found with ID: " + scanId));

        // 3. IDOR check — only owner (or admin/operator) may add images
        enforceOwnership(scan, currentUserId, currentUserRole, scanId);

        // 4. Status guard — cannot add images while processing or done
        ScanStatus status = scan.getStatus();
        if (status == ScanStatus.processing || status == ScanStatus.done) {
            throw new ScanConflictException(
                    "Cannot add images to a scan with status '" + status.name() + "'");
        }
        if (status == ScanStatus.deleted) {
            throw new ResourceNotFoundException("Scan not found with ID: " + scanId);
        }

        // 5. File format validation (MIME + extension)
        validateFileFormat(file);

        // 6. File size validation (≤ 20 MB)
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new FileSizeExceededException(
                    "File '" + file.getOriginalFilename() + "' exceeds the 20 MB limit");
        }

        // 7. Quota check — must have room before accepting the bytes
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));
        long storageUsed  = user.getStorageUsedBytes()  != null ? user.getStorageUsedBytes()  : 0L;
        long storageQuota = user.getStorageQuotaBytes() != null ? user.getStorageQuotaBytes() : 10737418240L;
        if (storageUsed + file.getSize() > storageQuota) {
            log.warn("Quota exceeded for user {}: used={}, quota={}, incoming={}",
                    currentUserId, storageUsed, storageQuota, file.getSize());
            throw new StorageQuotaExceededException("Xoá scan cũ để tiếp tục");
        }

        // 8. Determine needs_quality_check flag:
        //    TRUE  → a QC run already exists for this scan, so the QC engine should
        //            re-check only this new image and merge results (UC-04 step 4).
        //    FALSE → no QC run yet, this is a regular first-pass upload.
        boolean priorQcExists = Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM quality_check_runs WHERE scan_id = ?)",
                        Boolean.class, scanId));

        // 9. Compute next sort_order (append after existing images)
        int nextSortOrder = scanImageRepository.findMaxSortOrderByScanId(scanId) + 1;

        // 10. Determine format from original filename
        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown";
        String ext = extractExtension(originalFilename);

        // 11. Simulate storage persistence.
        //     In production this would delegate to a StorageService (S3 / MinIO).
        //     The storage_path convention follows /{user_id}/{scan_id}/{uuid}.{ext}
        UUID imageId = UUID.randomUUID();
        String storagePath = "/" + currentUserId + "/" + scanId + "/" + imageId + "." + ext;

        // 12. Read file bytes (needed for hash/metadata in production; kept simple here)
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes for scan {}: {}", scanId, e.getMessage());
            throw new InvalidFileFormatException("Failed to read file: " + e.getMessage());
        }

        // 13. Persist ScanImage record
        ScanImage image = ScanImage.builder()
                .id(imageId)
                .scan(scan)
                .originalFilename(originalFilename)
                .storagePath(storagePath)
                .thumbnailPath(null)          // generated asynchronously by a background job
                .fileSizeBytes(file.getSize())
                .mimeType(file.getContentType())
                .format(ext)
                .sortOrder(nextSortOrder)
                .uploadStatus(ScanImageUploadStatus.uploaded)
                .isExcluded(false)
                .needsQualityCheck(priorQcExists)
                .build();
        scanImageRepository.saveAndFlush(image);

        // 14. Update scan counters and promote status to 'uploading' if still draft
        scan.setFileCount(scan.getFileCount() + 1);
        scan.setTotalSizeBytes(scan.getTotalSizeBytes() + file.getSize());
        if (scan.getStatus() == ScanStatus.draft) {
            scan.setStatus(ScanStatus.uploading);
        }
        scan.setUpdatedAt(Instant.now());
        scanRepository.saveAndFlush(scan);

        // 15. Update user quota
        user.setStorageUsedBytes(storageUsed + file.getSize());
        userRepository.saveAndFlush(user);

        log.info("Image {} added to scan {} (sort_order={}, needsQC={})",
                imageId, scanId, nextSortOrder, priorQcExists);

        return ScanImageUploadResponse.builder()
                .image(ScanImageResponse.fromEntity(image))
                .scanFileCount(scan.getFileCount())
                .scanTotalSizeBytes(scan.getTotalSizeBytes())
                .build();
    }

    @Override
    @Transactional
    public com.memeto.team.memeto3d.dto.ScanVideoUploadResponse addVideoToScan(UUID scanId, MultipartFile file, UUID currentUserId, Role currentUserRole) {
        log.info("Uploading video to scan ID: {} by user: {}", scanId, currentUserId);

        if (file == null || file.isEmpty()) {
            throw new InvalidFileFormatException("No file provided or file is empty");
        }

        Scan scan = scanRepository.findByIdAndDeletedAtIsNull(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found with ID: " + scanId));

        enforceOwnership(scan, currentUserId, currentUserRole, scanId);

        // Check if scan is in image mode
        if (scan.getInputMode() == InputMode.image) {
            throw new ScanConflictException("Cannot upload video to a scan with input_mode 'image'");
        }

        ScanStatus status = scan.getStatus();
        if (status == ScanStatus.processing || status == ScanStatus.done) {
            throw new ScanConflictException("Cannot add video to a scan with status '" + status.name() + "'");
        }
        if (status == ScanStatus.deleted) {
            throw new ResourceNotFoundException("Scan not found with ID: " + scanId);
        }

        // File format validation (mp4, mov, avi)
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String ext = extractExtension(originalFilename).toLowerCase();
        if (!Set.of("mp4", "mov", "avi").contains(ext)) {
            throw new InvalidFileFormatException("File format not supported. Must be mp4, mov, or avi.");
        }

        // File size validation (<= 2GB)
        long MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024;
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new FileSizeExceededException("File exceeds the 2GB limit. Please use chunked upload.");
        }

        // Quota check
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));
        long storageUsed  = user.getStorageUsedBytes()  != null ? user.getStorageUsedBytes()  : 0L;
        long storageQuota = user.getStorageQuotaBytes() != null ? user.getStorageQuotaBytes() : 10737418240L;
        if (storageUsed + file.getSize() > storageQuota) {
            throw new StorageQuotaExceededException("Xoá scan cũ để tiếp tục");
        }

        // Simulate storage persistence
        UUID videoId = UUID.randomUUID();
        String storagePath = "/" + currentUserId + "/" + scanId + "/" + videoId + "." + ext;

        // Persist ScanVideo record
        com.memeto.team.memeto3d.entity.ScanVideo video = com.memeto.team.memeto3d.entity.ScanVideo.builder()
                .id(videoId)
                .scan(scan)
                .originalFilename(originalFilename)
                .storagePath(storagePath)
                .fileSizeBytes(file.getSize())
                .mimeType(file.getContentType())
                .format(ext)
                .build();
        
        scanVideoRepository.saveAndFlush(video);

        // Update scan counters and status
        scan.setFileCount(scan.getFileCount() + 1);
        scan.setTotalSizeBytes(scan.getTotalSizeBytes() + file.getSize());
        scan.setStatus(ScanStatus.uploaded);
        scan.setUpdatedAt(Instant.now());
        scanRepository.saveAndFlush(scan);

        // Update user quota
        user.setStorageUsedBytes(storageUsed + file.getSize());
        userRepository.saveAndFlush(user);

        return com.memeto.team.memeto3d.dto.ScanVideoUploadResponse.builder()
                .id(video.getId())
                .scanId(scan.getId())
                .originalFilename(video.getOriginalFilename())
                .storagePath(video.getStoragePath())
                .fileSizeBytes(video.getFileSizeBytes())
                .mimeType(video.getMimeType())
                .format(video.getFormat())
                .createdAt(video.getCreatedAt())
                .build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * IDOR check: throws ResourceNotFoundException (mapped to 404) if the
     * current user does not own the scan and is not admin/operator.
     */
    private void enforceOwnership(Scan scan, UUID currentUserId, Role currentUserRole, UUID scanId) {
        boolean isPrivileged = currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_OPERATOR;
        if (!isPrivileged && (scan.getUser() == null || !scan.getUser().getId().equals(currentUserId))) {
            log.warn("Access denied for user {} to scan {} owned by {}",
                    currentUserId, scanId, scan.getUser() != null ? scan.getUser().getId() : null);
            throw new ResourceNotFoundException("Scan not found with ID: " + scanId);
        }
    }

    /** Validates MIME type and file extension against allowed image formats. */
    private void validateFileFormat(MultipartFile file) {
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME_TYPES.contains(mime.toLowerCase())) {
            throw new InvalidFileFormatException(
                    "Unsupported file type '" + mime + "'. Allowed: image/jpeg, image/png");
        }
        String ext = extractExtension(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new InvalidFileFormatException(
                    "Unsupported file extension '." + ext + "'. Allowed: jpg, jpeg, png");
        }
    }

    /** Extracts lowercased file extension from filename, returns empty string if none. */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
