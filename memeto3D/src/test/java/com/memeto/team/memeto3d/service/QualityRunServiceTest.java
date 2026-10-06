package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.ImageQualityDetailResponse;
import com.memeto.team.memeto3d.entity.*;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.ImageQualityResultRepository;
import com.memeto.team.memeto3d.repository.QualityCheckRunRepository;
import com.memeto.team.memeto3d.service.impl.QualityRunServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QualityRunServiceTest {

    @Mock
    private QualityCheckRunRepository qualityCheckRunRepository;

    @Mock
    private ImageQualityResultRepository imageQualityResultRepository;

    @InjectMocks
    private QualityRunServiceImpl qualityRunService;

    private UUID userId;
    private UUID otherUserId;
    private UUID scanId;
    private UUID runId;
    private UUID imageId;
    private UUID duplicateImageId;
    private User user;
    private Scan scan;
    private QualityCheckRun run;
    private ScanImage scanImage;
    private ScanImage duplicateScanImage;
    private ImageQualityResult qualityResult;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        scanId = UUID.randomUUID();
        runId = UUID.randomUUID();
        imageId = UUID.randomUUID();
        duplicateImageId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("test@example.com")
                .role(Role.ROLE_USER)
                .build();

        scan = Scan.builder()
                .id(scanId)
                .user(user)
                .name("Test Scan Session")
                .status(ScanStatus.quality_check)
                .build();

        run = QualityCheckRun.builder()
                .id(runId)
                .scan(scan)
                .runNumber(1)
                .algoVersion("v1.0")
                .status("completed")
                .overallScore(BigDecimal.valueOf(85.5))
                .scoreCategory("green")
                .startedAt(Instant.now())
                .build();

        scanImage = ScanImage.builder()
                .id(imageId)
                .scan(scan)
                .originalFilename("sample_photo_01.jpg")
                .storagePath("uploads/scans/" + scanId + "/sample_photo_01.jpg")
                .thumbnailPath("https://res.cloudinary.com/demo/image/upload/v123/thumb_01.jpg")
                .fileSizeBytes(204800L)
                .mimeType("image/jpeg")
                .format("jpg")
                .build();

        duplicateScanImage = ScanImage.builder()
                .id(duplicateImageId)
                .scan(scan)
                .originalFilename("sample_photo_dup.jpg")
                .storagePath("uploads/scans/" + scanId + "/sample_photo_dup.jpg")
                .thumbnailPath("https://res.cloudinary.com/demo/image/upload/v123/thumb_dup.jpg")
                .fileSizeBytes(204800L)
                .mimeType("image/jpeg")
                .format("jpg")
                .build();

        qualityResult = ImageQualityResult.builder()
                .id(UUID.randomUUID())
                .qualityCheckRun(run)
                .scanImage(scanImage)
                .sharpnessScore(BigDecimal.valueOf(145.20))
                .isBlurry(false)
                .blurType(null)
                .meanBrightness(BigDecimal.valueOf(120.50))
                .isTooDark(false)
                .isTooBright(false)
                .saturatedPixelPct(BigDecimal.valueOf(0.50))
                .hasExposureWarning(false)
                .featureCount(3200)
                .isLowFeatures(false)
                .overlapScorePrev(BigDecimal.valueOf(82.00))
                .overlapScoreNext(BigDecimal.valueOf(79.00))
                .hasInsufficientOverlap(false)
                .isExactDuplicate(false)
                .isNearDuplicate(false)
                .aspectRatioWarning(false)
                .hasWatermarkWarning(false)
                .snrScore(BigDecimal.valueOf(42.10))
                .isHighNoise(false)
                .overallStatus("pass")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Successfully retrieve image quality detail for valid owner")
    void getImageQualityDetail_Success() {
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(imageQualityResultRepository.findByQualityCheckRunIdAndScanImageId(runId, imageId))
                .thenReturn(Optional.of(qualityResult));

        ImageQualityDetailResponse response = qualityRunService.getImageQualityDetail(
                runId, imageId, userId, Role.ROLE_USER);

        assertThat(response).isNotNull();
        assertThat(response.getRunId()).isEqualTo(runId);
        assertThat(response.getImageId()).isEqualTo(imageId);
        assertThat(response.getScanId()).isEqualTo(scanId);
        assertThat(response.getOriginalFilename()).isEqualTo("sample_photo_01.jpg");
        assertThat(response.getStoragePath()).isEqualTo("uploads/scans/" + scanId + "/sample_photo_01.jpg");
        assertThat(response.getBlurScore()).isEqualByComparingTo("145.20");
        assertThat(response.getBrightnessScore()).isEqualByComparingTo("120.50");
        assertThat(response.getExposureStatus()).isEqualTo("NORMAL");
        assertThat(response.getFeatureCount()).isEqualTo(3200);
        assertThat(response.getIsDuplicate()).isFalse();
        assertThat(response.getDuplicateOf()).isNull();
        assertThat(response.getOverallStatus()).isEqualTo("pass");

        verify(qualityCheckRunRepository).findById(runId);
        verify(imageQualityResultRepository).findByQualityCheckRunIdAndScanImageId(runId, imageId);
    }

    @Test
    @DisplayName("Successfully retrieve duplicate image info when image is an exact duplicate")
    void getImageQualityDetail_DuplicateImage_ReturnsDuplicateOf() {
        qualityResult.setIsExactDuplicate(true);
        qualityResult.setDuplicateOfImage(duplicateScanImage);
        qualityResult.setOverallStatus("warn");

        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(imageQualityResultRepository.findByQualityCheckRunIdAndScanImageId(runId, imageId))
                .thenReturn(Optional.of(qualityResult));

        ImageQualityDetailResponse response = qualityRunService.getImageQualityDetail(
                runId, imageId, userId, Role.ROLE_USER);

        assertThat(response).isNotNull();
        assertThat(response.getIsDuplicate()).isTrue();
        assertThat(response.getIsExactDuplicate()).isTrue();
        assertThat(response.getDuplicateOf()).isNotNull();
        assertThat(response.getDuplicateOf().getImageId()).isEqualTo(duplicateImageId);
        assertThat(response.getDuplicateOf().getOriginalFilename()).isEqualTo("sample_photo_dup.jpg");
        assertThat(response.getDuplicateOf().getDuplicateType()).isEqualTo("exact");

        assertThat(response.getFailureReasons()).anyMatch(reason -> "EXACT_DUPLICATE".equals(reason.getCode()));
    }

    @Test
    @DisplayName("Retrieve failure reasons and exposure status when image is too dark and blurry")
    void getImageQualityDetail_BlurryAndTooDark_ReturnsMappedRemediationDetails() {
        qualityResult.setIsBlurry(true);
        qualityResult.setBlurType("defocus_blur");
        qualityResult.setIsTooDark(true);
        qualityResult.setOverallStatus("fail");

        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(imageQualityResultRepository.findByQualityCheckRunIdAndScanImageId(runId, imageId))
                .thenReturn(Optional.of(qualityResult));

        ImageQualityDetailResponse response = qualityRunService.getImageQualityDetail(
                runId, imageId, userId, Role.ROLE_USER);

        assertThat(response).isNotNull();
        assertThat(response.getIsBlurry()).isTrue();
        assertThat(response.getBlurType()).isEqualTo("defocus_blur");
        assertThat(response.getIsTooDark()).isTrue();
        assertThat(response.getExposureStatus()).isEqualTo("TOO_DARK");
        assertThat(response.getOverallStatus()).isEqualTo("fail");

        assertThat(response.getFailureReasons()).extracting("code")
                .contains("BLURRY_IMAGE", "TOO_DARK");
        assertThat(response.getFailureReasons()).allMatch(detail -> detail.getSuggestion() != null && !detail.getSuggestion().isBlank());
    }

    @Test
    @DisplayName("Throw 404 ResourceNotFoundException when quality check run does not exist")
    void getImageQualityDetail_RunNotFound_Throws404() {
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qualityRunService.getImageQualityDetail(runId, imageId, userId, Role.ROLE_USER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Quality run not found");
    }

    @Test
    @DisplayName("Throw 404 ResourceNotFoundException on IDOR access attempt by unauthorized non-owner")
    void getImageQualityDetail_IdorAccessDenied_Throws404() {
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> qualityRunService.getImageQualityDetail(runId, imageId, otherUserId, Role.ROLE_USER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Quality run not found");
    }

    @Test
    @DisplayName("Allow ADMIN/OPERATOR to access quality detail of any scan (IDOR bypass)")
    void getImageQualityDetail_AdminUser_Success() {
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(imageQualityResultRepository.findByQualityCheckRunIdAndScanImageId(runId, imageId))
                .thenReturn(Optional.of(qualityResult));

        ImageQualityDetailResponse response = qualityRunService.getImageQualityDetail(
                runId, imageId, otherUserId, Role.ROLE_ADMIN);

        assertThat(response).isNotNull();
        assertThat(response.getImageId()).isEqualTo(imageId);
    }

    @Test
    @DisplayName("Throw 404 ResourceNotFoundException when image result does not belong to the specified run")
    void getImageQualityDetail_ImageNotBelongingToRun_Throws404() {
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(imageQualityResultRepository.findByQualityCheckRunIdAndScanImageId(runId, imageId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> qualityRunService.getImageQualityDetail(runId, imageId, userId, Role.ROLE_USER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Image quality result not found");
    }
}
