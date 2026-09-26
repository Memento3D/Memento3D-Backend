package com.memeto.team.memeto3d.entity;

/**
 * Upload status for a single image within a scan session.
 * Maps to the upload_status column in scan_images table.
 * - pending:   image record created, not yet transferred to storage
 * - uploaded:  successfully stored
 * - failed:    transfer or validation failed (still returned to FE for display)
 */
public enum ScanImageUploadStatus {
    pending,
    uploaded,
    failed;

    public static ScanImageUploadStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ScanImageUploadStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException(
                "Invalid upload_status: " + value + ". Valid values: pending, uploaded, failed");
    }
}
