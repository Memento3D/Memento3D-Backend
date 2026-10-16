package com.memento3d.printers.application.service;

import com.memento3d.printers.api.request.CreatePrinterRequest;
import com.memento3d.printers.api.response.PrinterResponse;
import com.memento3d.printers.domain.model.PrinterStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PrinterService {

    Page<PrinterResponse> list(PrinterStatus status, int page, int limit);

    PrinterResponse get(UUID id);

    PrinterResponse create(CreatePrinterRequest request, UUID actorId);

    PrinterResponse enterMaintenance(UUID id);

    PrinterResponse exitMaintenance(UUID id);
}
