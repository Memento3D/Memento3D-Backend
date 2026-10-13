package com.memento3d.shared.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error response")
public class ErrorResponse {

    @Builder.Default
    @Schema(description = "Error timestamp", example = "2026-09-20T10:15:30Z")
    private Instant timestamp = Instant.now();

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Error message", example = "Invalid input or validation error")
    private String message;

    @Schema(description = "Request URI path", example = "/api/auth/login")
    private String path;

    @Schema(description = "Field-level validation error map, if applicable")
    private Map<String, String> validationErrors;
}
