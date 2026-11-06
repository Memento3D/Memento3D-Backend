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
        this.apiKey = apiKey == null ? "" : apiKey.trim();

        URI uri = URI.create(this.baseUrl);

        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "AI base URL must be a valid HTTP or HTTPS URL"
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
        requireConfiguredApiKey();
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
        requireConfiguredApiKey();
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

        return result;
    }

    @Override
    public com.memento3d.quality.domain.port.ImageValidationProvider.ImageValidationResult validateImage(String imageUrl) {
        requireConfiguredApiKey();
        String body;
        try {
            body = mapper.writeValueAsString(java.util.Map.of("input_url", imageUrl));
        } catch (IOException ex) {
            throw new AiClientException("Failed to serialize AI image-validation request", ex);
        }

        HttpResponse<String> response = send(
                requestBuilder("/validate-image")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build()
        );

        if (response.statusCode() != 200) {
            return com.memento3d.quality.domain.port.ImageValidationProvider.ImageValidationResult
                    .failed("Validation returned HTTP " + response.statusCode());
        }

        try {
            var tree = mapper.readTree(response.body());
            boolean valid = tree.path("valid").asBoolean();
            String status = tree.path("quality_status").asText("FAILED");
            String format = tree.path("format").asText("");
            long size = tree.path("file_size_bytes").asLong(0L);
            String message = tree.path("message").asText("AI image validation completed.");
            return new com.memento3d.quality.domain.port.ImageValidationProvider.ImageValidationResult(
                    valid, status, format, size, message
            );
        } catch (IOException ex) {
            throw new AiClientException("Invalid AI image-validation response", ex);
        }
    }

    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("ngrok-skip-browser-warning", "1");
        if (!apiKey.isBlank()) {
            builder.header("X-API-Key", apiKey);
        }
        return builder;
    }

    private void requireConfiguredApiKey() {
        if (apiKey.isBlank()) {
            throw new AiClientException(
                    "AI_API_KEY is not configured",
                    (Integer) null
            );
        }
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
    public com.memento3d.reconstruction.application.dto.AiCustomizationManifest.Download getCustomizationManifest(UUID jobId,UUID attemptId) {
        byte[] bytes=downloadBounded("/jobs/"+jobId+"/customization?attempt_id="+attemptId,64*1024);
        try {
            var scene=mapper.readValue(bytes,com.memento3d.reconstruction.application.dto.AiCustomizationManifest.class);
            return new com.memento3d.reconstruction.application.dto.AiCustomizationManifest.Download(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),scene);
        } catch(IOException ex) { throw new AiClientException("Invalid AI customization manifest",ex); }
    }
    @Override
    public byte[] downloadCustomizationAsset(UUID jobId,UUID attemptId,String assetId) {
        if(assetId==null || !assetId.matches("[a-z0-9][a-z0-9-]{0,99}"))throw new IllegalArgumentException("Invalid fitted asset identifier");
        return requireGlb(downloadBounded("/jobs/"+jobId+"/customization/assets/"+assetId+"?attempt_id="+attemptId,50*1024*1024));
    }
    @Override
    public byte[] downloadGlb(UUID jobId,UUID attemptId) {
        return requireGlb(downloadBounded("/jobs/"+jobId+"/outputs/glb?attempt_id="+attemptId,50*1024*1024));
    }
    private byte[] requireGlb(byte[] bytes) {
        if(bytes.length<12 || bytes[0]!='g' || bytes[1]!='l' || bytes[2]!='T' || bytes[3]!='F')
            throw new AiClientException("AI returned an invalid GLB header",(Integer)null);
        return bytes;
    }
    private byte[] downloadBounded(String path,int maxBytes) {
        requireConfiguredApiKey();
        try {
            var response=httpClient.send(requestBuilder(path).timeout(Duration.ofMinutes(2)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream input=response.body()) {
                if(response.statusCode()!=200)throw new AiClientException("AI artifact download returned HTTP "+response.statusCode(),response.statusCode());
                byte[] bytes=input.readNBytes(maxBytes+1);
                if(bytes.length>maxBytes)throw new AiClientException("AI artifact exceeds the download limit",(Integer)null);
                return bytes;
            }
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();throw new AiClientException("AI artifact download was interrupted",ex);
        } catch(IOException ex) { throw new AiClientException("Failed to download AI artifact",ex); }
    }
}
