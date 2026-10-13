package com.memento3d.assets.domain.model;

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
