package com.memento3d.models.api;

import com.memento3d.scans.domain.model.Scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.models.api.request.CreateModelRatingRequest;
import com.memento3d.models.api.response.ModelRatingResponse;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.models.application.service.ModelRatingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModelRatingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ModelRatingService modelRatingService;

    @Test
    @DisplayName("POST /api/v1/scans/{scan_id}/ratings - Valid rating returns 200 OK")
    void testCreateOrUpdateRating_Success() throws Exception {
        UUID scanId = UUID.randomUUID();
        CreateModelRatingRequest request = CreateModelRatingRequest.builder()
                .rating(5)
                .comment("Excellent quality!")
                .build();

        ModelRatingResponse mockResponse = ModelRatingResponse.builder()
                .id(UUID.randomUUID())
                .scanId(scanId)
                .rating(5)
                .comment("Excellent quality!")
                .createdAt(Instant.now())
                .build();

        when(modelRatingService.createOrUpdateRating(eq(scanId), any(CreateModelRatingRequest.class), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/scans/{scan_id}/ratings", scanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").value(scanId.toString()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Excellent quality!"));
    }

    @Test
    @DisplayName("POST /api/v1/scans/{scan_id}/ratings - Rating out of bounds (0 or 6) returns 400 Bad Request")
    void testCreateOrUpdateRating_InvalidRating_Returns400() throws Exception {
        UUID scanId = UUID.randomUUID();
        CreateModelRatingRequest request = CreateModelRatingRequest.builder()
                .rating(6)
                .comment("Too high")
                .build();

        mockMvc.perform(post("/api/v1/scans/{scan_id}/ratings", scanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/scans/{scan_id}/ratings - Scan not found returns 404 Not Found")
    void testCreateOrUpdateRating_ScanNotFound_Returns404() throws Exception {
        UUID scanId = UUID.randomUUID();
        CreateModelRatingRequest request = CreateModelRatingRequest.builder()
                .rating(4)
                .build();

        when(modelRatingService.createOrUpdateRating(eq(scanId), any(CreateModelRatingRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Scan session not found with ID: " + scanId));

        mockMvc.perform(post("/api/v1/scans/{scan_id}/ratings", scanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
