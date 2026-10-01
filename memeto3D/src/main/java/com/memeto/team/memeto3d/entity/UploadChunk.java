package com.memeto.team.memeto3d.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks individual chunks of a chunked file upload session.
 * Maps to the {@code upload_chunks} table.
 *
 * <p>A "file_reference" is the logical identifier for the upload session
 * (e.g. filename or a client-generated session UUID). Together with
 * {@code scan_id} it forms the composite key that groups all chunks
 * belonging to the same file upload.</p>
 */
@Entity
@Table(
    name = "upload_chunks",
    indexes = {
        @Index(name = "idx_upload_chunks_scan", columnList = "scan_id, file_reference")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_upload_chunks_scan_ref_idx",
            columnNames = {"scan_id", "file_reference", "chunk_index"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The scan this upload belongs to. Cascade-deletes when scan is removed. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    /**
     * Logical identifier for the upload session.
     * Typically the original filename or a client-generated session ID.
     */
    @Column(name = "file_reference", nullable = false, length = 255)
    private String fileReference;

    /** Zero-based index of this chunk within the full file (0 ... chunk_total-1). */
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    /** Byte size of this particular chunk. */
    @Column(name = "chunk_size_bytes", nullable = false)
    private Long chunkSizeBytes;

    /** Internal path where this chunk is stored (null while pending). */
    @Column(name = "storage_path", length = 500)
    private String storagePath;

    /**
     * Lifecycle status of this chunk.
     * pending | uploaded | failed | cancelled
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UploadChunkStatus status = UploadChunkStatus.pending;

    /** Timestamp when this chunk was successfully stored (null while pending/failed). */
    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
