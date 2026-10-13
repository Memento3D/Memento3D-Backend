package com.memento3d.assets.domain.model;

import com.memento3d.scans.domain.model.InputMode;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.domain.model.ScanStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link UploadChunk} builder, getters/setters, and default values (C0).
 * Uses pure Java — no Spring context required.
 */
@DisplayName("UploadChunk entity")
class UploadChunkTest {

    @Test
    @DisplayName("Builder defaults: status=pending, storagePath=null, uploadedAt=null")
    void builderDefaults() {
        Scan scan = Scan.builder()
                .id(UUID.randomUUID()).name("s")
                .inputMode(InputMode.video).status(ScanStatus.draft)
                .fileCount(0).totalSizeBytes(0L).build();

        UploadChunk chunk = UploadChunk.builder()
                .id(UUID.randomUUID())
                .scan(scan)
                .fileReference("file.mp4")
                .chunkIndex(0)
                .chunkSizeBytes(1024L)
                .build();

        assertThat(chunk.getStatus()).isEqualTo(UploadChunkStatus.pending);
        assertThat(chunk.getStoragePath()).isNull();
        assertThat(chunk.getUploadedAt()).isNull();
    }

    @Test
    @DisplayName("Setters and all fields accessible")
    void settersAndGetters() {
        UUID id     = UUID.randomUUID();
        Instant now = Instant.now();
        Scan scan   = Scan.builder()
                .id(UUID.randomUUID()).name("s")
                .inputMode(InputMode.image).status(ScanStatus.uploading)
                .fileCount(0).totalSizeBytes(0L).build();

        UploadChunk chunk = UploadChunk.builder()
                .id(id).scan(scan)
                .fileReference("ref.mp4")
                .chunkIndex(3)
                .chunkSizeBytes(2048L)
                .storagePath("/storage/chunk_3")
                .status(UploadChunkStatus.uploaded)
                .uploadedAt(now)
                .build();

        assertThat(chunk.getId()).isEqualTo(id);
        assertThat(chunk.getScan()).isSameAs(scan);
        assertThat(chunk.getFileReference()).isEqualTo("ref.mp4");
        assertThat(chunk.getChunkIndex()).isEqualTo(3);
        assertThat(chunk.getChunkSizeBytes()).isEqualTo(2048L);
        assertThat(chunk.getStoragePath()).isEqualTo("/storage/chunk_3");
        assertThat(chunk.getStatus()).isEqualTo(UploadChunkStatus.uploaded);
        assertThat(chunk.getUploadedAt()).isEqualTo(now);

        // Setter coverage
        chunk.setStatus(UploadChunkStatus.failed);
        assertThat(chunk.getStatus()).isEqualTo(UploadChunkStatus.failed);

        chunk.setStoragePath("/new/path");
        assertThat(chunk.getStoragePath()).isEqualTo("/new/path");

        chunk.setUploadedAt(null);
        assertThat(chunk.getUploadedAt()).isNull();
    }

    @Test
    @DisplayName("No-args constructor creates object without throwing")
    void noArgsConstructor() {
        UploadChunk chunk = new UploadChunk();
        assertThat(chunk).isNotNull();
    }

    @Test
    @DisplayName("All-args constructor sets all fields")
    void allArgsConstructor() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Scan scan = Scan.builder()
                .id(UUID.randomUUID()).name("s")
                .inputMode(InputMode.video).status(ScanStatus.draft)
                .fileCount(0).totalSizeBytes(0L).build();

        UploadChunk chunk = new UploadChunk(
                id, scan, "all.mp4", 7, 4096L, "/s/c7",
                UploadChunkStatus.cancelled, now, now);

        assertThat(chunk.getId()).isEqualTo(id);
        assertThat(chunk.getFileReference()).isEqualTo("all.mp4");
        assertThat(chunk.getChunkIndex()).isEqualTo(7);
        assertThat(chunk.getStatus()).isEqualTo(UploadChunkStatus.cancelled);
        assertThat(chunk.getUploadedAt()).isEqualTo(now);
        assertThat(chunk.getCreatedAt()).isEqualTo(now);
    }
}