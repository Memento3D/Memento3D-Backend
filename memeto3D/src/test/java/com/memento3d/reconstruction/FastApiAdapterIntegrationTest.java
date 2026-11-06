package com.memento3d.reconstruction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.quality.domain.port.ImageValidationProvider;
import com.memento3d.quality.infrastructure.ai.FastApiImageValidationAdapter;
import com.memento3d.quality.infrastructure.ai.MockImageValidationProvider;
import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.application.dto.AiSubmitJobRequest;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.reconstruction.domain.port.ReconstructionProvider;
import com.memento3d.reconstruction.infrastructure.ai.FastApiReconstructionAdapter;
import com.memento3d.reconstruction.infrastructure.ai.HttpAiGenerationClient;
import com.memento3d.reconstruction.domain.exception.AiClientException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FastApiAdapterIntegrationTest {

    @Mock
    private AiGenerationClient aiClient;

    @InjectMocks
    private FastApiReconstructionAdapter reconstructionAdapter;

    @InjectMocks
    private FastApiImageValidationAdapter imageValidationAdapter;

    @Test
    @DisplayName("FastApiReconstructionAdapter submits job to client and maps response")
    void testReconstructionAdapterSubmit() {
        UUID jobId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID frontImageId = UUID.randomUUID();
        UUID leftImageId = UUID.randomUUID();
        UUID rightImageId = UUID.randomUUID();

        AiSubmitJobRequest request = new AiSubmitJobRequest(
                jobId,
                attemptId,
                List.of(
                        new AiSubmitJobRequest.InputImage(
                                frontImageId, "https://example.com/front.png", "front"),
                        new AiSubmitJobRequest.InputImage(
                                leftImageId, "https://example.com/left.png", "left"),
                        new AiSubmitJobRequest.InputImage(
                                rightImageId, "https://example.com/right.png", "right")
                ),
                "default"
        );
        JsonNode requestJson = new ObjectMapper().valueToTree(request);
        assertEquals(3, requestJson.path("input_images").size());
        assertEquals("front", requestJson.path("input_images").get(0).path("view_hint").asText());
        assertFalse(requestJson.has("input_image_id"));
        AiJobResponse mockResponse = new AiJobResponse(
                jobId,
                attemptId,
                "running",
                "preprocessing",
                15,
                null,
                null,
                Instant.now(),
                Instant.now(),
                null
        );

        when(aiClient.submitJob(any(AiSubmitJobRequest.class))).thenReturn(mockResponse);

        assertEquals("FASTAPI", reconstructionAdapter.getProviderCode());
        assertEquals(3, reconstructionAdapter.getMinImages());
        assertEquals(4, reconstructionAdapter.getMaxImages());

        AiJobResponse response = reconstructionAdapter.submit(request);

        assertNotNull(response);
        assertEquals(jobId, response.jobId());
        assertEquals(attemptId, response.attemptId());
        assertEquals("running", response.status());
        verify(aiClient, times(1)).submitJob(request);
    }

    @Test
    @DisplayName("HTTP client sends the multi-view contract and configured API key")
    void testHttpClientSubmitsMultiViewRequest() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jobs", exchange -> {
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            ));
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            byte[] response = ("{\"job_id\":\"" + jobId
                    + "\",\"attempt_id\":\"" + attemptId
                    + "\",\"status\":\"queued\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        try {
            HttpAiGenerationClient client = new HttpAiGenerationClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "shared-headapi-key"
            );
            AiSubmitJobRequest request = new AiSubmitJobRequest(
                    jobId,
                    attemptId,
                    List.of(
                            new AiSubmitJobRequest.InputImage(
                                    UUID.randomUUID(), "https://res.cloudinary.com/front.jpg", "front"),
                            new AiSubmitJobRequest.InputImage(
                                    UUID.randomUUID(), "https://res.cloudinary.com/left.jpg", "left"),
                            new AiSubmitJobRequest.InputImage(
                                    UUID.randomUUID(), "https://res.cloudinary.com/right.jpg", "right")
                    ),
                    "default"
            );

            AiJobResponse response = client.submitJob(request);

            assertEquals(jobId, response.jobId());
            assertEquals("shared-headapi-key", apiKey.get());
            JsonNode sent = new ObjectMapper().readTree(requestBody.get());
            assertEquals(3, sent.path("input_images").size());
            assertEquals("front", sent.path("input_images").get(0).path("view_hint").asText());
            assertEquals("default", sent.path("profile").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("FastApiReconstructionAdapter gets GLB bytes and checks status")
    void testReconstructionAdapterStatusAndGlb() {
        UUID jobId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        AiJobResponse mockStatus = new AiJobResponse(
                jobId,
                attemptId,
                "done",
                "exporting",
                100,
                null,
                null,
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(50),
                Instant.now()
        );

        byte[] glbBytes = new byte[]{'g', 'l', 'T', 'F', 0x02, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00, 0x00};

        when(aiClient.getJob(jobId)).thenReturn(mockStatus);
        when(aiClient.downloadGlb(jobId, attemptId)).thenReturn(glbBytes);

        AiJobResponse status = reconstructionAdapter.getStatus(jobId, null);
        assertNotNull(status);
        assertEquals("done", status.status());

        byte[] downloaded = reconstructionAdapter.getResultGlb(jobId, attemptId, null);
        assertNotNull(downloaded);
        assertEquals(12, downloaded.length);
        assertEquals((byte) 'g', downloaded[0]);
        assertEquals((byte) 'l', downloaded[1]);
        assertEquals((byte) 'T', downloaded[2]);
        assertEquals((byte) 'F', downloaded[3]);
    }

    @Test
    @DisplayName("FastApiImageValidationAdapter validates image format and passes result")
    void testImageValidationAdapter() {
        UUID scanId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        String imageUrl = "https://res.cloudinary.com/memento3d/image/upload/v1/sample.png";

        when(aiClient.validateImage(imageUrl)).thenReturn(
                ImageValidationProvider.ImageValidationResult.passed("png", 4096L, "Image format OK")
        );

        assertEquals("FASTAPI", imageValidationAdapter.getProviderCode());
        ImageValidationProvider.ImageValidationResult result = imageValidationAdapter.validate(scanId, imageId, imageUrl);

        assertNotNull(result);
        assertTrue(result.valid());
        assertEquals("PASSED", result.qualityStatus());
        assertEquals("png", result.format());
    }

    @Test
    @DisplayName("FastApiImageValidationAdapter does not convert an unavailable service into a pass")
    void testImageValidationFailureDoesNotPass() {
        when(aiClient.validateImage(any())).thenThrow(
                new AiClientException("AI_API_KEY is not configured", (Integer) null)
        );

        ImageValidationProvider.ImageValidationResult result =
                imageValidationAdapter.validate(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "https://res.cloudinary.com/memento3d/image/upload/v1/sample.png"
                );

        assertFalse(result.valid());
        assertEquals("FAILED", result.qualityStatus());
    }

    @Test
    @DisplayName("MockImageValidationProvider passes validation cleanly")
    void testMockImageValidationProvider() {
        MockImageValidationProvider mockProvider = new MockImageValidationProvider();
        assertEquals("MOCK", mockProvider.getProviderCode());

        ImageValidationProvider.ImageValidationResult result = mockProvider.validate(
                UUID.randomUUID(), UUID.randomUUID(), "http://localhost/mock.jpg"
        );

        assertNotNull(result);
        assertTrue(result.valid());
        assertEquals("PASSED", result.qualityStatus());
    }
}
