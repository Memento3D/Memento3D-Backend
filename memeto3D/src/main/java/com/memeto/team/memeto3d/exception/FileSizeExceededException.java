package com.memeto.team.memeto3d.exception;

/** Thrown when an uploaded file exceeds the maximum allowed size (20 MB). */
public class FileSizeExceededException extends RuntimeException {
    public FileSizeExceededException(String message) {
        super(message);
    }
}