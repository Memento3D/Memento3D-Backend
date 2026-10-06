package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.DuplicateImageInfo;
import com.memeto.team.memeto3d.dto.FailureReasonDetail;
import com.memeto.team.memeto3d.dto.ImageQualityDetailResponse;
import com.memeto.team.memeto3d.entity.*;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.ImageQualityResultRepository;
import com.memeto.team.memeto3d.repository.QualityCheckRunRepository;
import com.memeto.team.memeto3d.service.QualityRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QualityRunServiceImpl implements QualityRunService {

    private final QualityCheckRunRepository qualityCheckRunRepository;
    private final ImageQualityResultRepository imageQualityResultRepository;

    @Override
    @Transactional(readOnly = true)
    public ImageQualityDetailResponse getImageQualityDetail(UUID runId, UUID imageId, UUID currentUserId, Role currentUserRole) {
        log.info("Fetching image quality detail for run ID: {}, image ID: {}, requested by user ID: {}, role: {}",
                runId, imageId, currentUserId, currentUserRole);

        // 1. Fetch QualityCheckRun
        QualityCheckRun run = qualityCheckRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Quality run not found with ID: " + runId));

        Scan scan = run.getScan();
        if (scan == null || scan.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Quality run not found with ID: " + runId);
        }

        // 2. IDOR check
        boolean isPrivileged = currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_OPERATOR;
        if (!isPrivileged && (scan.getUser() == null || !scan.getUser().getId().equals(currentUserId))) {
            log.warn("Access denied for user {} to quality run {} owned by {}",
                    currentUserId, runId, scan.getUser() != null ? scan.getUser().getId() : null);
            throw new ResourceNotFoundException("Quality run not found with ID: " + runId);
        }

        // 3. Fetch ImageQualityResult by runId + imageId
        ImageQualityResult result = imageQualityResultRepository.findByQualityCheckRunIdAndScanImageId(runId, imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image quality result not found for run ID: " + runId + " and image ID: " + imageId));

        ScanImage scanImage = result.getScanImage();
        if (scanImage == null || scanImage.getDeletedAt() != null
                || scanImage.getScan() == null || !scanImage.getScan().getId().equals(scan.getId())) {
            throw new ResourceNotFoundException("Image quality result not found for run ID: " + runId + " and image ID: " + imageId);
        }

        // 4. Determine duplicate status & duplicate_of details
        boolean isExactDup = Boolean.TRUE.equals(result.getIsExactDuplicate());
        boolean isNearDup = Boolean.TRUE.equals(result.getIsNearDuplicate());
        boolean isDuplicate = isExactDup || isNearDup || result.getDuplicateOfImage() != null || result.getDuplicateGroupId() != null;

        DuplicateImageInfo duplicateOfInfo = null;
        if (isDuplicate) {
            ScanImage targetDupImage = result.getDuplicateOfImage();

            if (targetDupImage == null && result.getDuplicateGroupId() != null) {
                List<ImageQualityResult> groupResults = imageQualityResultRepository
                        .findByQualityCheckRunIdAndDuplicateGroupId(runId, result.getDuplicateGroupId());
                for (ImageQualityResult groupRes : groupResults) {
                    if (groupRes.getScanImage() != null && !groupRes.getScanImage().getId().equals(imageId)) {
                        targetDupImage = groupRes.getScanImage();
                        break;
                    }
                }
            }

            if (targetDupImage != null) {
                String dupType = isExactDup ? "exact" : (isNearDup ? "near" : "exact");
                duplicateOfInfo = DuplicateImageInfo.builder()
                        .imageId(targetDupImage.getId())
                        .originalFilename(targetDupImage.getOriginalFilename())
                        .thumbnailPath(targetDupImage.getThumbnailPath())
                        .storagePath(targetDupImage.getStoragePath())
                        .duplicateType(dupType)
                        .build();
            }
        }

        // 5. Exposure status determination
        String exposureStatus = "NORMAL";
        if (Boolean.TRUE.equals(result.getIsTooDark())) {
            exposureStatus = "TOO_DARK";
        } else if (Boolean.TRUE.equals(result.getIsTooBright())) {
            exposureStatus = "TOO_BRIGHT";
        } else if (Boolean.TRUE.equals(result.getHasExposureWarning())) {
            exposureStatus = "EXPOSURE_WARNING";
        }

        // 6. Overlap score calculation (prefer explicit overlap_score_prev or average)
        BigDecimal overlapScore = result.getOverlapScorePrev();
        if (overlapScore == null && result.getOverlapScoreNext() != null) {
            overlapScore = result.getOverlapScoreNext();
        } else if (overlapScore != null && result.getOverlapScoreNext() != null) {
            overlapScore = overlapScore.add(result.getOverlapScoreNext()).divide(BigDecimal.valueOf(2));
        }

        // 7. Failure reasons and remediation suggestions list
        List<FailureReasonDetail> failureReasonDetails = buildFailureReasonDetails(result, isExactDup, isNearDup);

        // 8. Image URL resolution
        String imageUrl = scanImage.getThumbnailPath();
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = scanImage.getStoragePath();
        }

        return ImageQualityDetailResponse.builder()
                .id(result.getId())
                .runId(run.getId())
                .imageId(scanImage.getId())
                .scanId(scan.getId())
                .originalFilename(scanImage.getOriginalFilename())
                .storagePath(scanImage.getStoragePath())
                .imageUrl(imageUrl)
                .thumbnailPath(scanImage.getThumbnailPath())
                .overallStatus(result.getOverallStatus() != null ? result.getOverallStatus() : "pass")

                // Blur / Sharpness
                .blurScore(result.getSharpnessScore())
                .isBlurry(result.getIsBlurry())
                .blurType(result.getBlurType())

                // Brightness / Exposure
                .brightnessScore(result.getMeanBrightness())
                .exposureStatus(exposureStatus)
                .isTooDark(result.getIsTooDark())
                .isTooBright(result.getIsTooBright())
                .saturatedPixelPct(result.getSaturatedPixelPct())
                .hasExposureWarning(result.getHasExposureWarning())

                // Feature & Overlap
                .featureCount(result.getFeatureCount())
                .isLowFeatures(result.getIsLowFeatures())
                .overlapScore(overlapScore)
                .overlapScorePrev(result.getOverlapScorePrev())
                .overlapScoreNext(result.getOverlapScoreNext())
                .hasInsufficientOverlap(result.getHasInsufficientOverlap())

                // Duplicate
                .isDuplicate(isDuplicate)
                .isExactDuplicate(result.getIsExactDuplicate())
                .isNearDuplicate(result.getIsNearDuplicate())
                .duplicateGroupId(result.getDuplicateGroupId())
                .duplicateOf(duplicateOfInfo)

                // Technical metrics
                .snrScore(result.getSnrScore())
                .isHighNoise(result.getIsHighNoise())
                .watermarkAreaPct(result.getWatermarkAreaPct())
                .hasWatermarkWarning(result.getHasWatermarkWarning())
                .aspectRatioWarning(result.getAspectRatioWarning())

                // Remediation suggestions for FE
                .failureReasons(failureReasonDetails)
                .createdAt(result.getCreatedAt())
                .build();
    }

    private List<FailureReasonDetail> buildFailureReasonDetails(ImageQualityResult result, boolean isExactDup, boolean isNearDup) {
        List<FailureReasonDetail> details = new ArrayList<>();
        Set<String> processedCodes = new HashSet<>();

        // Add explicit string failure reasons if present
        if (result.getFailureReasons() != null) {
            for (String rawReason : result.getFailureReasons()) {
                if (rawReason != null && !rawReason.isBlank()) {
                    String code = rawReason.trim().toUpperCase();
                    if (processedCodes.add(code)) {
                        details.add(createDetailFromCode(code));
                    }
                }
            }
        }

        // Check condition flags
        if (Boolean.TRUE.equals(result.getIsBlurry()) && processedCodes.add("BLURRY_IMAGE")) {
            details.add(FailureReasonDetail.builder()
                    .code("BLURRY_IMAGE")
                    .message("Ảnh bị mờ nhòe do chuyển động hoặc mất nét")
                    .suggestion("Giữ chắc thiết bị hoặc sử dụng chân máy (tripod) khi chụp để hình ảnh sắc nét.")
                    .build());
        }

        if (Boolean.TRUE.equals(result.getIsTooDark()) && processedCodes.add("TOO_DARK")) {
            details.add(FailureReasonDetail.builder()
                    .code("TOO_DARK")
                    .message("Thiếu ánh sáng (ảnh quá tối)")
                    .suggestion("Tăng ánh sáng môi trường hoặc bổ sung đèn chiếu sáng đối tượng.")
                    .build());
        }

        if (Boolean.TRUE.equals(result.getIsTooBright()) && processedCodes.add("TOO_BRIGHT")) {
            details.add(FailureReasonDetail.builder()
                    .code("TOO_BRIGHT")
                    .message("Dư sáng (ảnh bị chói / cháy sáng)")
                    .suggestion("Giảm bớt nguồn sáng trực tiếp hoặc điều chỉnh thông số phơi sáng.")
                    .build());
        }

        if (Boolean.TRUE.equals(result.getIsLowFeatures()) && processedCodes.add("LOW_FEATURES")) {
            details.add(FailureReasonDetail.builder()
                    .code("LOW_FEATURES")
                    .message("Số lượng chi tiết đặc trưng quá thấp")
                    .suggestion("Tránh chụp các bề mặt quá nhẵn, đơn sắc; bổ sung họa tiết quanh vật thể.")
                    .build());
        }

        if (Boolean.TRUE.equals(result.getHasInsufficientOverlap()) && processedCodes.add("INSUFFICIENT_OVERLAP")) {
            details.add(FailureReasonDetail.builder()
                    .code("INSUFFICIENT_OVERLAP")
                    .message("Độ đè phủ giữa các ảnh chụp không đủ")
                    .suggestion("Chụp các góc gần nhau hơn (di chuyển nhỏ khoảng 10-15 độ giữa các ảnh).")
                    .build());
        }

        if (isExactDup && processedCodes.add("EXACT_DUPLICATE")) {
            details.add(FailureReasonDetail.builder()
                    .code("EXACT_DUPLICATE")
                    .message("Ảnh bị trùng lặp hoàn toàn")
                    .suggestion("Xoá bớt ảnh trùng lặp để tiết kiệm thời gian xử lý 3D.")
                    .build());
        }

        if (isNearDup && processedCodes.add("NEAR_DUPLICATE")) {
            details.add(FailureReasonDetail.builder()
                    .code("NEAR_DUPLICATE")
                    .message("Ảnh có góc chụp gần như trùng lặp")
                    .suggestion("Loại bỏ ảnh trùng góc để đảm bảo tập ảnh chụp phân bổ đều xung quanh vật thể.")
                    .build());
        }

        if (Boolean.TRUE.equals(result.getIsHighNoise()) && processedCodes.add("HIGH_NOISE")) {
            details.add(FailureReasonDetail.builder()
                    .code("HIGH_NOISE")
                    .message("Nhiễu hạt (noise) cao")
                    .suggestion("Chụp trong điều kiện đủ sáng với mức ISO thấp hơn.")
                    .build());
        }

        if (Boolean.TRUE.equals(result.getHasWatermarkWarning()) && processedCodes.add("WATERMARK_WARNING")) {
            details.add(FailureReasonDetail.builder()
                    .code("WATERMARK_WARNING")
                    .message("Phát hiện hình mờ (watermark) hoặc logo")
                    .suggestion("Loại bỏ logo/watermark xuất hiện trên hình ảnh.")
                    .build());
        }

        if (Boolean.TRUE.equals(result.getAspectRatioWarning()) && processedCodes.add("ASPECT_RATIO_WARNING")) {
            details.add(FailureReasonDetail.builder()
                    .code("ASPECT_RATIO_WARNING")
                    .message("Tỉ lệ khung hình không đồng nhất")
                    .suggestion("Giữ nguyên tỉ lệ khung hình chụp (VD: 4:3 hoặc 16:9) cho toàn bộ bộ ảnh.")
                    .build());
        }

        return details;
    }

    private FailureReasonDetail createDetailFromCode(String code) {
        return switch (code) {
            case "BLURRY_IMAGE", "MOTION_BLUR", "DEFOCUS_BLUR" -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Ảnh bị mờ nhòe do chuyển động hoặc mất nét")
                    .suggestion("Giữ chắc thiết bị hoặc sử dụng chân máy (tripod) khi chụp để hình ảnh sắc nét.")
                    .build();
            case "TOO_DARK" -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Thiếu ánh sáng (ảnh quá tối)")
                    .suggestion("Tăng ánh sáng môi trường hoặc bổ sung đèn chiếu sáng đối tượng.")
                    .build();
            case "TOO_BRIGHT" -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Dư sáng (ảnh bị chói / cháy sáng)")
                    .suggestion("Giảm bớt nguồn sáng trực tiếp hoặc điều chỉnh thông số phơi sáng.")
                    .build();
            case "LOW_FEATURES" -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Số lượng chi tiết đặc trưng quá thấp")
                    .suggestion("Tránh chụp các bề mặt quá nhẵn, đơn sắc; bổ sung họa tiết quanh vật thể.")
                    .build();
            case "INSUFFICIENT_OVERLAP" -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Độ đè phủ giữa các ảnh chụp không đủ")
                    .suggestion("Chụp các góc gần nhau hơn (di chuyển nhỏ khoảng 10-15 độ giữa các ảnh).")
                    .build();
            case "EXACT_DUPLICATE" -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Ảnh bị trùng lặp hoàn toàn")
                    .suggestion("Xoá bớt ảnh trùng lặp để tiết kiệm thời gian xử lý 3D.")
                    .build();
            case "NEAR_DUPLICATE" -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Ảnh có góc chụp gần như trùng lặp")
                    .suggestion("Loại bỏ ảnh trùng góc để đảm bảo tập ảnh chụp phân bổ đều xung quanh vật thể.")
                    .build();
            case "HIGH_NOISE" -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Nhiễu hạt (noise) cao")
                    .suggestion("Chụp trong điều kiện đủ sáng với mức ISO thấp hơn.")
                    .build();
            case "WATERMARK_WARNING" -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Phát hiện hình mờ (watermark) hoặc logo")
                    .suggestion("Loại bỏ logo/watermark xuất hiện trên hình ảnh.")
                    .build();
            case "ASPECT_RATIO_WARNING" -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Tỉ lệ khung hình không đồng nhất")
                    .suggestion("Giữ nguyên tỉ lệ khung hình chụp (VD: 4:3 hoặc 16:9) cho toàn bộ bộ ảnh.")
                    .build();
            default -> FailureReasonDetail.builder()
                    .code(code)
                    .message("Phát hiện vấn đề chất lượng: " + code)
                    .suggestion("Kiểm tra lại hình ảnh theo hướng dẫn quy chuẩn chụp ảnh 3D.")
                    .build();
        };
    }
}
