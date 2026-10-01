package com.memeto.team.memeto3d.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType; // e.g. "IMAGE", "VIDEO"

    @Column(name = "target_id")
    private UUID targetId; // the ID of ScanImage or ScanVideo if created

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "chunk_total", nullable = false)
    private Integer chunkTotal;

    @Column(name = "chunk_uploaded", nullable = false)
    @Builder.Default
    private Integer chunkUploaded = 0;

    @Column(name = "upload_status", nullable = false, length = 20)
    @Builder.Default
    private String uploadStatus = "pending"; // pending, completed, failed

    @Column(name = "expected_checksum", length = 64)
    private String expectedChecksum;
    
    @Column(name = "uploaded_chunks_bitmap", columnDefinition = "TEXT")
    private String uploadedChunksBitmap; // comma separated indices like "0,1,2"

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
