package com.memento3d.assets.domain.exception;

/** Thrown when an uploaded file exceeds the maximum allowed size (20 MB). */
public class FileSizeExceededException extends RuntimeException {
    public FileSizeExceededException(String message) {
        super(message);
    }
}