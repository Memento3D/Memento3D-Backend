package com.memeto.team.memeto3d.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers all enum constants in {@link UploadChunkStatus} (C0).
 */
@DisplayName("UploadChunkStatus enum")
class UploadChunkStatusTest {

    @Test
    @DisplayName("All four constants exist and values() is complete")
    void allConstantsPresent() {
        assertThat(UploadChunkStatus.values()).containsExactlyInAnyOrder(
                UploadChunkStatus.pending,
                UploadChunkStatus.uploaded,
                UploadChunkStatus.failed,
                UploadChunkStatus.cancelled
        );
    }

    @Test
    @DisplayName("valueOf works for each constant name")
    void valueOfEachConstant() {
        assertThat(UploadChunkStatus.valueOf("pending")).isEqualTo(UploadChunkStatus.pending);
        assertThat(UploadChunkStatus.valueOf("uploaded")).isEqualTo(UploadChunkStatus.uploaded);
        assertThat(UploadChunkStatus.valueOf("failed")).isEqualTo(UploadChunkStatus.failed);
        assertThat(UploadChunkStatus.valueOf("cancelled")).isEqualTo(UploadChunkStatus.cancelled);
    }
}