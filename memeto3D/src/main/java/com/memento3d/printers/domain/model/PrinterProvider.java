package com.memento3d.printers.domain.model;

/** Firmware / control-plane the printer is driven through. */
public enum PrinterProvider {
    OCTOPRINT,
    ELEGOO_SATURN_RESIN,
    CHITU_MANAGER_LAN,
    USB_MANUAL,
    OTHER
}
