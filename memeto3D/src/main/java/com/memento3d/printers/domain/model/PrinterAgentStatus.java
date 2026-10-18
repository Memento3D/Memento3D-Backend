package com.memento3d.printers.domain.model;

public enum PrinterAgentStatus {
    ONLINE,
    OFFLINE,
    /** Credential permanently disabled; heartbeats are rejected. */
    REVOKED
}
