package com.memento3d.printers.infrastructure.adapter;

import com.memento3d.printers.domain.port.PrinterIntegrationPort;
import com.memento3d.printjob.domain.model.ResinHandoffMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Component("manualResinPrinterAdapter")
@Slf4j
public class ManualResinPrinterAdapter implements PrinterIntegrationPort {

    @Override
    public String getProviderCode() {
        return "ELEGOO_SATURN_RESIN";
    }

    @Override
    public Set<PrinterCapability> getCapabilities() {
        // Manual adapter only supports file handoff logging. No remote start/pause/telemetry claimed.
        return Collections.singleton(PrinterCapability.FILE_HANDOFF);
    }

    @Override
    public HandoffResult handoffFile(UUID printJobId, String sliceFileUrl, ResinHandoffMethod method, UUID operatorId) {
        log.info("File handoff recorded for job {} via method {} by operator {}", printJobId, method, operatorId);
        String ref = "HANDOFF-" + method.name() + "-" + System.currentTimeMillis();
        String message = method == ResinHandoffMethod.USB_MANUAL
                ? "File cắt lớp đã sẵn sàng để sao chép vào USB cắm trực tiếp vào máy in ELEGOO Saturn 4 Ultra 16K."
                : "File cắt lớp đã sẵn sàng để gửi qua ChituManager trên máy tính cùng mạng nội bộ LAN.";
        return HandoffResult.success(message, ref);
    }
}
