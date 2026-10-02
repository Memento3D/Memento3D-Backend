package com.memeto.team.memeto3d.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ImageQualityResultShortResponse {
    private UUID imageId;
    private String status; // 'pass', 'warn', 'fail'
}
