package com.memeto.team.memeto3d.entity;

/**
 * Lifecycle status of a single upload chunk, mirroring the DB CHECK constraint
 * on {@code upload_chunks.status}.
 */
public enum UploadChunkStatus {
    pending,
    uploaded,
    failed,
    cancelled
}
