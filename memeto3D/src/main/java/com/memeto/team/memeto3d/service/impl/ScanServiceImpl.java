package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ScanResponse;
import com.memeto.team.memeto3d.entity.InputMode;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.entity.ScanStatus;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.exception.StorageQuotaExceededException;
import com.memeto.team.memeto3d.repository.ScanRepository;
import com.memeto.team.memeto3d.repository.UserRepository;
import com.memeto.team.memeto3d.service.ScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
