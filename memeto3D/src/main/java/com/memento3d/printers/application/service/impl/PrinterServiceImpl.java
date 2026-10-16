package com.memento3d.printers.application.service.impl;

import com.memento3d.printers.api.request.CreatePrinterRequest;
import com.memento3d.printers.api.response.PrinterResponse;
import com.memento3d.printers.application.mapper.PrinterMapper;
import com.memento3d.printers.application.service.PrinterService;
import com.memento3d.printers.domain.exception.PrinterAgentNotFoundException;
import com.memento3d.printers.domain.exception.PrinterConflictException;
import com.memento3d.printers.domain.exception.PrinterNotFoundException;
import com.memento3d.printers.domain.model.Printer;
import com.memento3d.printers.domain.model.PrinterAgent;
import com.memento3d.printers.domain.model.PrinterStatus;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterAgentRepository;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrinterServiceImpl implements PrinterService {

    static final int MAX_PAGE_SIZE = 100;

    private final PrinterRepository printerRepository;
    private final PrinterAgentRepository agentRepository;
    private final PrinterMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Page<PrinterResponse> list(PrinterStatus status, int page, int limit) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 1) - 1,
                Math.min(Math.max(limit, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "name"));
        Page<Printer> result = status == null
                ? printerRepository.findAll(pageable)
                : printerRepository.findByStatus(status, pageable);
        return result.map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PrinterResponse get(UUID id) {
        return mapper.toResponse(printerRepository.findWithAgentById(id)
                .orElseThrow(() -> new PrinterNotFoundException(id)));
    }

    @Override
    @Transactional
    public PrinterResponse create(CreatePrinterRequest request, UUID actorId) {
        boolean hasAgent = request.getAgentId() != null;
        boolean hasRemoteId = request.getOctoprintPrinterId() != null && !request.getOctoprintPrinterId().isBlank();
        if (hasAgent != hasRemoteId) {
            throw new IllegalArgumentException("agentId and octoprintPrinterId must be provided together");
        }

        String serial = request.getSerialNumber().trim();
        if (printerRepository.existsBySerialNumberIgnoreCase(serial)) {
            throw new PrinterConflictException("PRINTER_SERIAL_ALREADY_EXISTS",
                    "A printer with serial number '" + serial + "' already exists");
        }

        PrinterAgent agent = null;
        String remoteId = null;
        if (hasAgent) {
            agent = agentRepository.findById(request.getAgentId())
                    .orElseThrow(() -> new PrinterAgentNotFoundException(request.getAgentId()));
            if (agent.isRevoked()) {
                throw new PrinterConflictException("PRINTER_AGENT_REVOKED", "Cannot attach a printer to a revoked agent");
            }
            remoteId = request.getOctoprintPrinterId().trim();
            if (printerRepository.existsByAgentIdAndOctoprintPrinterId(agent.getId(), remoteId)) {
                throw new PrinterConflictException("PRINTER_ALREADY_MAPPED",
                        "Agent already has a printer mapped to '" + remoteId + "'");
            }
        }

        Printer printer = Printer.builder()
                .name(request.getName().trim())
                .serialNumber(serial)
                .model(request.getModel().trim())
                .provider(request.getProvider())
                .status(PrinterStatus.OFFLINE) // status always starts OFFLINE; learned from the agent
                .agent(agent)
                .octoprintPrinterId(remoteId)
                .createdBy(actorId)
                .build();
        try {
            printer = printerRepository.saveAndFlush(printer);
        } catch (DataIntegrityViolationException e) {
            throw new PrinterConflictException("PRINTER_DUPLICATE", "Printer serial number or agent mapping already exists");
        }
        log.info("Printer {} registered by {}", printer.getId(), actorId);
        return mapper.toResponse(printer);
    }

    @Override
    @Transactional
    public PrinterResponse enterMaintenance(UUID id) {
        Printer printer = printerRepository.findByIdForUpdate(id).orElseThrow(() -> new PrinterNotFoundException(id));
        printer.enterMaintenance();
        log.info("Printer {} entered maintenance", id);
        return mapper.toResponse(printer);
    }

    @Override
    @Transactional
    public PrinterResponse exitMaintenance(UUID id) {
        Printer printer = printerRepository.findByIdForUpdate(id).orElseThrow(() -> new PrinterNotFoundException(id));
        printer.exitMaintenance();
        log.info("Printer {} left maintenance", id);
        return mapper.toResponse(printer);
    }
}
