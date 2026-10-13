package com.memento3d.quality.domain.exception;

/** Thrown when a quality override is attempted on a run that cannot be overridden (red score). Mapped to 403. */
public class QualityOverrideForbiddenException extends RuntimeException {
    public QualityOverrideForbiddenException(String message) {
        super(message);
    }
}
