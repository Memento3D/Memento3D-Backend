package com.memento3d.assets.domain.exception;

/** Thrown when an uploaded file has an unsupported MIME type or format. */
public class InvalidFileFormatException extends RuntimeException {
    public InvalidFileFormatException(String message) {
        super(message);
    }
}