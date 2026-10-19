package com.memento3d.admin.controller;

import com.memento3d.printers.api.response.PrinterResponse;
import com.memento3d.printers.application.service.PrinterService;
import com.memento3d.printers.domain.model.PrinterStatus;
import com.memento3d.printjob.api.response.PrintJobResponse;
import com.memento3d.printjob.domain.model.PrintJob;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Hardware & Print Jobs", description = "Printer fleet telemetry and factory print job production monitoring")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPrinterController {

    private final PrinterService printerService;
    private final PrintJobRepository printJobRepository;

    @GetMapping("/printers")
    @Operation(summary = "Monitor 3D printer fleet telemetry and agent connection status")
    public ResponseEntity<Page<PrinterResponse>> listPrinters(
            @RequestParam(name = "status", required = false) PrinterStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(printerService.list(status, page, size));
    }

    @GetMapping("/print-jobs")
    @Operation(summary = "Monitor factory 3D print jobs, materials consumed, and printer allocations")
    public ResponseEntity<Page<PrintJobResponse>> listPrintJobs(
            @RequestParam(name = "status", required = false) PrintJobStatus status,
            @RequestParam(name = "printerId", required = false) UUID printerId,
            @RequestParam(name = "materialId", required = false) UUID materialId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PrintJob> jobs = printJobRepository.findAllWithFilters(status, printerId, materialId, pageRequest);
        return ResponseEntity.ok(jobs.map(PrintJobResponse::fromEntity));
    }
}
