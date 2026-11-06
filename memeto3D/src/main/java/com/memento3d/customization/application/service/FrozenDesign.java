package com.memento3d.customization.application.service;

import java.util.*;

/** Stable storage references, never signed URLs or a pointer to a mutable draft. */
public record FrozenDesign(UUID modelOutputId, UUID fitResultId, UUID userId, UUID scanId,
        UUID jobId, UUID attemptId, long revision, String catalogCode, String bodyPreset,
        Map<String,UUID> selection, Artifact base, List<Artifact> assets) {
    public record Artifact(UUID assetVersionId, String name, String slot, String provider,
            String objectKey, String location, String sha256, long sizeBytes, List<Double> matrix) {}
}
