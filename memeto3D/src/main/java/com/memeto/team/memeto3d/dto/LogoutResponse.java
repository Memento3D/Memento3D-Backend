package com.memeto.team.memeto3d.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Logout response")
public class LogoutResponse {

    @Schema(description = "Status message", example = "Logout successful")
    private String message;

    @Schema(description = "Additional details", example = "Token has been revoked and blacklisted")
    private String details;
}
