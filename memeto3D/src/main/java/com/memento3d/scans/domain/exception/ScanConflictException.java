package com.memento3d.scans.domain.exception;

public class ScanConflictException extends RuntimeException {
    public ScanConflictException(String message) {
        super(message);
    }
}
