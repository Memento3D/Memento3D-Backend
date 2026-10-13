package com.memento3d.scans.domain.model;

public enum ScanStatus {
    draft,
    uploading,
    uploaded,
    quality_check,
    ready,
    processing,
    done,
    failed,
    deleted;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (ScanStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    public static ScanStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ScanStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid status: " + value + ". Valid statuses are: draft, uploading, uploaded, quality_check, ready, processing, done, failed, deleted");
    }
}
