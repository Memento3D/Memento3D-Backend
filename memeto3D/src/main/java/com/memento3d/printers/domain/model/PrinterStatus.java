package com.memento3d.printers.domain.model;

public enum PrinterStatus {
    OFFLINE,
    IDLE,
    PREPARING,
    PRINTING,
    PAUSED,
    ERROR,
    MAINTENANCE;

    /** Statuses a printer agent is allowed to report (MAINTENANCE is a manager-owned state). */
    public boolean isAgentReportable() {
        return this != MAINTENANCE;
    }

    /** True while the machine is physically occupied by a print. */
    public boolean isBusy() {
        return this == PREPARING || this == PRINTING || this == PAUSED;
    }
}
