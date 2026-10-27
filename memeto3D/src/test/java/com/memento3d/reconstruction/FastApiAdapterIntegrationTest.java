package com.memento3d.reconstruction;

import com.memento3d.quality.domain.port.ImageValidationProvider;
import com.memento3d.quality.infrastructure.ai.FastApiImageValidationAdapter;
import com.memento3d.quality.infrastructure.ai.MockImageValidationProvider;
import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.application.dto.AiSubmitJobRequest;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.reconstruction.domain.port.ReconstructionProvider;
import com.memento3d.reconstruction.infrastructure.ai.FastApiReconstructionAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

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
        UUID imageId = UUID.randomUUID();

        AiSubmitJobRequest request = new AiSubmitJobRequest(jobId, attemptId, imageId, "https://example.com/image.png", "high");
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
        assertEquals(1, reconstructionAdapter.getMinImages());
        assertEquals(50, reconstructionAdapter.getMaxImages());

        AiJobResponse response = reconstructionAdapter.submit(request);

        assertNotNull(response);
        assertEquals(jobId, response.jobId());
        assertEquals(attemptId, response.attemptId());
        assertEquals("running", response.status());
        verify(aiClient, times(1)).submitJob(request);
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
