package com.memento3d.quality.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memento3d.scans.api.response.DuplicateImageInfo;
import com.memento3d.quality.api.response.FailureReasonDetail;
import com.memento3d.quality.api.response.ImageQualityDetailResponse;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.shared.api.GlobalExceptionHandler;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.quality.application.service.QualityRunService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QualityRunControllerTest {

    @Mock
    private QualityRunService qualityRunService;

    @InjectMocks
    private QualityRunController qualityRunController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;
    private UUID testUserId;
    private UUID runId;
    private UUID imageId;
    private UUID scanId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(qualityRunController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testUserId = UUID.randomUUID();
        runId = UUID.randomUUID();
        imageId = UUID.randomUUID();
        scanId = UUID.randomUUID();

        User testUser = User.builder()
                .id(testUserId)
                .email("user@example.com")
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .build();
        mockUserDetails = new CustomUserDetails(testUser);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                mockUserDetails, null, mockUserDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/quality-runs/{run_id}/images/{image_id} returns 200 OK with full image metrics")
    void getImageQualityDetail_Success() throws Exception {
        UUID dupImageId = UUID.randomUUID();
        DuplicateImageInfo dupInfo = DuplicateImageInfo.builder()
                .imageId(dupImageId)
                .originalFilename("IMG_0001.JPG")
                .thumbnailPath("https://res.cloudinary.com/demo/thumb1.jpg")
                .storagePath("uploads/scans/123/IMG_0001.JPG")
                .duplicateType("exact")
                .build();

        FailureReasonDetail failureReason = FailureReasonDetail.builder()
                .code("EXACT_DUPLICATE")
                .message("Ảnh bị trùng lặp hoàn toàn")
                .suggestion("Xoá bớt ảnh trùng lặp để tiết kiệm bộ nhớ.")
                .build();

        ImageQualityDetailResponse response = ImageQualityDetailResponse.builder()
                .id(UUID.randomUUID())
                .runId(runId)
                .imageId(imageId)
                .scanId(scanId)
                .originalFilename("IMG_0042.JPG")
                .storagePath("uploads/scans/" + scanId + "/IMG_0042.JPG")
                .imageUrl("https://res.cloudinary.com/demo/IMG_0042.jpg")
                .thumbnailPath("https://res.cloudinary.com/demo/thumb_IMG_0042.jpg")
                .overallStatus("warn")
                .blurScore(BigDecimal.valueOf(142.30))
                .isBlurry(false)
                .brightnessScore(BigDecimal.valueOf(118.50))
                .exposureStatus("NORMAL")
                .isTooDark(false)
                .isTooBright(false)
                .featureCount(3500)
                .isLowFeatures(false)
                .overlapScore(BigDecimal.valueOf(82.50))
                .isDuplicate(true)
                .isExactDuplicate(true)
                .isNearDuplicate(false)
                .duplicateOf(dupInfo)
                .failureReasons(List.of(failureReason))
                .createdAt(Instant.now())
                .build();

        when(qualityRunService.getImageQualityDetail(eq(runId), eq(imageId), eq(testUserId), eq(Role.ROLE_USER)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/quality-runs/{run_id}/images/{image_id}", runId, imageId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_id", is(runId.toString())))
                .andExpect(jsonPath("$.image_id", is(imageId.toString())))
                .andExpect(jsonPath("$.scan_id", is(scanId.toString())))
                .andExpect(jsonPath("$.original_filename", is("IMG_0042.JPG")))
                .andExpect(jsonPath("$.storage_path", is("uploads/scans/" + scanId + "/IMG_0042.JPG")))
                .andExpect(jsonPath("$.blur_score", is(142.30)))
                .andExpect(jsonPath("$.brightness_score", is(118.50)))
                .andExpect(jsonPath("$.exposure_status", is("NORMAL")))
                .andExpect(jsonPath("$.feature_count", is(3500)))
                .andExpect(jsonPath("$.is_duplicate", is(true)))
                .andExpect(jsonPath("$.duplicate_of.image_id", is(dupImageId.toString())))
                .andExpect(jsonPath("$.duplicate_of.original_filename", is("IMG_0001.JPG")))
                .andExpect(jsonPath("$.failure_reasons[0].code", is("EXACT_DUPLICATE")))
                .andExpect(jsonPath("$.failure_reasons[0].suggestion", is("Xoá bớt ảnh trùng lặp để tiết kiệm bộ nhớ.")));
    }

    @Test
    @DisplayName("GET /api/v1/quality-runs/{run_id}/images/{image_id} returns 404 when image/run not found or IDOR denied")
    void getImageQualityDetail_NotFound_Returns404() throws Exception {
        when(qualityRunService.getImageQualityDetail(eq(runId), eq(imageId), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new ResourceNotFoundException("Quality run not found with ID: " + runId));

        mockMvc.perform(get("/api/v1/quality-runs/{run_id}/images/{image_id}", runId, imageId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Quality run not found with ID: " + runId)));
    }
}
