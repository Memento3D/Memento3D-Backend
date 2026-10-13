package com.memento3d.quality.api.response;

import com.memento3d.scans.domain.model.ScanStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityOverrideResponse {
    private UUID scanId;
    private UUID runId;
    private String scoreCategory;
    private String scanStatus;
    private Instant overriddenAt;
    private UUID overriddenBy;
}
