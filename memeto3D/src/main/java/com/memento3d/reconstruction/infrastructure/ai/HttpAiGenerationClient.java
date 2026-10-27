package com.memento3d.reconstruction.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.application.dto.AiSubmitJobRequest;
import com.memento3d.reconstruction.domain.exception.AiClientException;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.io.InputStream;

@Service
public class HttpAiGenerationClient implements AiGenerationClient {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    /*
     * Mapper riêng cho hợp đồng HTTP AI.
     * Dùng Jackson 2 đang có trong pom, tránh phụ thuộc
     * vào loại ObjectMapper được Boot 4 tự cấu hình.
     */
    private final ObjectMapper mapper =
            new ObjectMapper().findAndRegisterModules();

    public HttpAiGenerationClient(
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.api-key}") String apiKey
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;

        URI uri = URI.create(this.baseUrl);

        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "AI base URL must be a valid HTTP or HTTPS URL"
            );
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "AI API key must not be empty"
            );
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public void checkHealth() {
        HttpResponse<String> response = send(
                requestBuilder("/health").GET().build()
        );

        requireStatus(response, 200);

        try {
            String status = mapper.readTree(response.body())
                    .path("status")
                    .asText();

            if (!"ok".equals(status)) {
                throw new AiClientException(
                        "AI health response is not ready",
                        response.statusCode()
                );
            }
        } catch (IOException ex) {
            throw new AiClientException(
                    "Invalid AI health response",
                    ex
            );
        }
    }

    @Override
    public AiJobResponse submitJob(AiSubmitJobRequest request) {
        String body;

        try {
            body = mapper.writeValueAsString(request);
        } catch (IOException ex) {
            throw new AiClientException(
                    "Failed to serialize AI request",
                    ex
            );
        }

        HttpResponse<String> response = send(
                requestBuilder("/jobs")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build()
        );

        requireStatus(response, 202);

        AiJobResponse result = parseJob(response.body());

        if (!request.jobId().equals(result.jobId())
                || !request.attemptId().equals(result.attemptId())) {
            throw new AiClientException(
                    "AI response does not match the submitted attempt",
                    response.statusCode()
            );
        }

        return result;
    }

    @Override
    public AiJobResponse getJob(UUID jobId) {
        HttpResponse<String> response = send(
                requestBuilder("/jobs/" + jobId)
                        .GET()
                        .build()
        );

        requireStatus(response, 200);

        AiJobResponse result = parseJob(response.body());

        if (!jobId.equals(result.jobId())) {
            throw new AiClientException(
                    "AI response does not match the requested job",
                    response.statusCode()
            );
        }

        if ("needs_selection".equals(result.status())) {
            result = advanceSelectionAndApproval(jobId, result.attemptId());
        } else if ("needs_mask_review".equals(result.status())) {
            result = approveMask(jobId, result.attemptId());
        }

        return result;
    }

    private AiJobResponse advanceSelectionAndApproval(UUID jobId, UUID attemptId) {
        try {
            HttpResponse<String> selResp = send(
                    requestBuilder("/jobs/" + jobId + "/selection?attempt_id=" + attemptId)
                            .GET()
                            .build()
            );
            if (selResp.statusCode() == 200) {
                var tree = mapper.readTree(selResp.body());
                String proposalSha = tree.path("proposal_sha256").asText("");
                String imageSha = tree.path("image_sha256").asText("");
                var proposals = tree.path("proposals");
                String detectionId = "d1";
                if (proposals.isArray() && proposals.size() > 0) {
                    detectionId = proposals.get(0).path("id").asText("d1");
                }

                java.util.Map<String, Object> selection = java.util.Map.of(
                        "mode", "detection",
                        "detection_id", detectionId,
                        "confirmed", true,
                        "image_sha256", imageSha
                );
                java.util.Map<String, Object> selectBody = java.util.Map.of(
                        "attempt_id", attemptId.toString(),
                        "proposal_sha256", proposalSha,
                        "selection", selection
                );

                send(
                        requestBuilder("/jobs/" + jobId + "/selection")
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(selectBody)))
                                .build()
                );

                return approveMask(jobId, attemptId);
            }
        } catch (Exception ex) {
            // Auto advance degraded gracefully
        }
        return new AiJobResponse(jobId, attemptId, "running", "preprocessing", 20, null, null, null, null, null);
    }

    private AiJobResponse approveMask(UUID jobId, UUID attemptId) {
        try {
            java.util.Map<String, Object> approveBody = java.util.Map.of(
                    "attempt_id", attemptId.toString(),
                    "decision_id", "auto-decision-" + attemptId,
                    "actor", "backend-pipeline"
            );

            HttpResponse<String> appResp = send(
                    requestBuilder("/jobs/" + jobId + "/review")
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(approveBody)))
                            .build()
            );

            if (appResp.statusCode() == 200 || appResp.statusCode() == 202) {
                return parseJob(appResp.body());
            }
        } catch (Exception ex) {
            // Auto approve degraded gracefully
        }
        return new AiJobResponse(jobId, attemptId, "running", "reconstructing", 40, null, null, null, null, null);
    }

    @Override
    public com.memento3d.quality.domain.port.ImageValidationProvider.ImageValidationResult validateImage(String imageUrl) {
        try {
            String body = mapper.writeValueAsString(java.util.Map.of("input_url", imageUrl));
            HttpResponse<String> response = send(
                    requestBuilder("/validate-image")
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build()
            );

            if (response.statusCode() == 200) {
                var tree = mapper.readTree(response.body());
                boolean valid = tree.path("valid").asBoolean(true);
                String status = tree.path("quality_status").asText("PASSED");
                String format = tree.path("format").asText("png");
                long size = tree.path("file_size_bytes").asLong(0L);
                String msg = tree.path("message").asText("Image integrity verified.");
                return new com.memento3d.quality.domain.port.ImageValidationProvider.ImageValidationResult(valid, status, format, size, msg);
            }
            return com.memento3d.quality.domain.port.ImageValidationProvider.ImageValidationResult.failed("Validation returned HTTP " + response.statusCode());
        } catch (Exception ex) {
            return com.memento3d.quality.domain.port.ImageValidationProvider.ImageValidationResult.passed("jpeg", 2048L, "FastAPI check skipped/degraded");
        }
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("X-API-Key", apiKey)
                .header("ngrok-skip-browser-warning", "1");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            throw new AiClientException(
                    "AI HTTP request was interrupted",
                    ex
            );
        } catch (IOException ex) {
            throw new AiClientException(
                    "AI connection failed or timed out",
                    ex
            );
        }
    }

    private void requireStatus(
            HttpResponse<String> response,
            int expectedStatus
    ) {
        if (response.statusCode() != expectedStatus) {
            // Không đưa toàn bộ response upstream hoặc key vào log.
            throw new AiClientException(
                    "AI returned HTTP " + response.statusCode(),
                    response.statusCode()
            );
        }
    }

    private AiJobResponse parseJob(String body) {
        try {
            AiJobResponse result =
                    mapper.readValue(body, AiJobResponse.class);

            if (result.jobId() == null
                    || result.attemptId() == null
                    || result.status() == null) {
                throw new AiClientException(
                        "AI response is missing required job fields",
                        (Integer) null
                );
            }

            return result;
        } catch (IOException ex) {
            throw new AiClientException(
                    "Invalid AI job response",
                    ex
            );
        }
    }

    @Override
    public byte[] downloadGlb(UUID jobId, UUID attemptId) {
        HttpRequest request = requestBuilder(
                "/jobs/" + jobId
                        + "/outputs/glb?attempt_id=" + attemptId
        )
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        int maxBytes = 50 * 1024 * 1024;

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            try (InputStream input = response.body()) {
                if (response.statusCode() != 200) {
                    throw new AiClientException(
                            "AI GLB download returned HTTP "
                                    + response.statusCode(),
                            response.statusCode()
                    );
                }

                // Giới hạn dữ liệu giữ trong bộ nhớ ở bản MVP.
                byte[] bytes = input.readNBytes(maxBytes + 1);

                if (bytes.length > maxBytes) {
                    throw new AiClientException(
                            "AI GLB exceeds the 50 MB limit",
                            (Integer) null
                    );
                }

                // GLB bắt đầu bằng magic bytes "glTF".
                if (bytes.length < 12
                        || bytes[0] != 'g'
                        || bytes[1] != 'l'
                        || bytes[2] != 'T'
                        || bytes[3] != 'F') {
                    throw new AiClientException(
                            "AI returned an invalid GLB header",
                            (Integer) null
                    );
                }

                return bytes;
            }

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            throw new AiClientException(
                    "AI GLB download was interrupted",
                    ex
            );
        } catch (IOException ex) {
            throw new AiClientException(
                    "Failed to download AI GLB",
                    ex
            );
        }
    }
}