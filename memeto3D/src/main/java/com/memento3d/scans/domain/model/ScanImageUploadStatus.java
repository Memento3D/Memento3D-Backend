package com.memento3d.scans.domain.model;

/**
 * Upload status for a single image within a scan session.
 * - pending:   record created, not yet stored
 * - uploaded:  successfully stored
 * - failed:    transfer or validation failed (always returned to FE)
 */
public enum ScanImageUploadStatus {
    pending,
    uploaded,
    failed;

    public static ScanImageUploadStatus fromString(String value) {
        if (value == null || value.isBlank()) return null;
        for (ScanImageUploadStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) return s;
        }
        throw new IllegalArgumentException(
            "Invalid upload_status: " + value + ". Valid: pending, uploaded, failed");
    }
}