package com.memeto.team.memeto3d.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Detailed failure or warning reason with mapped FE remediation suggestions")
public class FailureReasonDetail {

    @Schema(description = "Standard reason code", example = "BLURRY_IMAGE")
    private String code;

    @Schema(description = "Human-readable description of the issue", example = "Ảnh bị mờ nhòe do chuyển động")
    private String message;

    @Schema(description = "Actionable suggestion to fix or prevent the issue on FE", example = "Giữ chắc thiết bị hoặc sử dụng chân máy (tripod) khi chụp để tránh mờ nhòe.")
    private String suggestion;
}
