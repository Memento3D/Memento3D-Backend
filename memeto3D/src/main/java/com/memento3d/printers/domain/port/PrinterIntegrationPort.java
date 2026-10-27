package com.memento3d.printers.domain.port;

import com.memento3d.printjob.domain.model.ResinHandoffMethod;

import java.util.Set;
import java.util.UUID;

public interface PrinterIntegrationPort {

    String getProviderCode();

    Set<PrinterCapability> getCapabilities();

    HandoffResult handoffFile(UUID printJobId, String sliceFileUrl, ResinHandoffMethod method, UUID operatorId);

    enum PrinterCapability {
        FILE_HANDOFF,
        TELEMETRY,
        REMOTE_START,
        REMOTE_PAUSE,
        REMOTE_CANCEL
    }

    record HandoffResult(
            boolean accepted,
            String message,
            String handoffReference
    ) {
        public static HandoffResult success(String message, String reference) {
            return new HandoffResult(true, message, reference);
        }

        public static HandoffResult notSupported(String message) {
            return new HandoffResult(false, message, null);
        }
    }
}
