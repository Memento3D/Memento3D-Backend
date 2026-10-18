package com.memento3d.printjob.domain.model;

public enum PrintJobStatus {
    CREATED,
    RESERVED,
    ISSUED,
    PRINTING,
    COMPLETED,
    FAILED,
    CANCELLED
}
