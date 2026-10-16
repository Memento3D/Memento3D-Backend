package com.memento3d.printers.application.service.impl;

import com.memento3d.printers.api.request.AgentHeartbeatRequest;
import com.memento3d.printers.api.request.RegisterPrinterAgentRequest;
import com.memento3d.printers.api.response.AgentHeartbeatResponse;
import com.memento3d.printers.api.response.PrinterAgentCredentialResponse;
import com.memento3d.printers.api.response.PrinterAgentResponse;
import com.memento3d.printers.application.mapper.PrinterMapper;
import com.memento3d.printers.application.service.PrinterAgentService;
import com.memento3d.printers.domain.exception.AgentUnauthorizedException;
import com.memento3d.printers.domain.exception.PrinterAgentNotFoundException;
import com.memento3d.printers.domain.exception.PrinterConflictException;
import com.memento3d.printers.domain.model.Printer;
import com.memento3d.printers.domain.model.PrinterAgent;
import com.memento3d.printers.domain.model.PrinterAgentStatus;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterAgentRepository;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.printers.infrastructure.security.AgentTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrinterAgentServiceImpl implements PrinterAgentService {

    private final PrinterAgentRepository agentRepository;
    private final PrinterRepository printerRepository;
    private final AgentTokenService tokenService;
    private final PrinterMapper mapper;

    @Value("${app.printing.agent.poll-interval-seconds:5}")
    private int pollIntervalSeconds = 5;

    @Override
    @Transactional
    public PrinterAgentCredentialResponse register(RegisterPrinterAgentRequest request, UUID actorId) {
        String name = request.getName().trim();
        if (agentRepository.existsByNameIgnoreCase(name)) {
            throw new PrinterConflictException("PRINTER_AGENT_NAME_ALREADY_EXISTS",
                    "A printer agent named '" + name + "' already exists");
        }
        String token = tokenService.generateToken();
        PrinterAgent agent = PrinterAgent.builder()
                .name(name)
                .description(request.getDescription())
                .tokenHash(tokenService.hash(token))
                .status(PrinterAgentStatus.OFFLINE)
                .createdBy(actorId)
                .build();
        try {
            agent = agentRepository.saveAndFlush(agent);
        } catch (DataIntegrityViolationException e) {
            throw new PrinterConflictException("PRINTER_AGENT_NAME_ALREADY_EXISTS",
                    "A printer agent named '" + name + "' already exists");
        }
        log.info("Printer agent {} registered by {}", agent.getId(), actorId); // never log the token
        return PrinterAgentCredentialResponse.builder().agent(mapper.toResponse(agent)).token(token).build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrinterAgentResponse> list(int page, int limit) {
        return agentRepository.findAll(PageRequest.of(
                        Math.max(page, 1) - 1,
                        Math.min(Math.max(limit, 1), PrinterServiceImpl.MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.ASC, "name")))
                .map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PrinterAgentResponse get(UUID id) {
        return mapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public PrinterAgentCredentialResponse rotateToken(UUID id) {
        PrinterAgent agent = find(id);
        if (agent.isRevoked()) {
            throw new PrinterConflictException("PRINTER_AGENT_REVOKED", "Revoked agents cannot be rotated");
        }
        String token = tokenService.generateToken();
        agent.rotateToken(tokenService.hash(token), Instant.now());
        log.info("Token rotated for printer agent {}", id);
        return PrinterAgentCredentialResponse.builder().agent(mapper.toResponse(agent)).token(token).build();
    }

    @Override
    @Transactional
    public PrinterAgentResponse revoke(UUID id) {
        PrinterAgent agent = find(id);
        agent.revoke();
        printerRepository.findByAgentId(id).forEach(Printer::markOffline);
        log.warn("Printer agent {} revoked", id);
        return mapper.toResponse(agent);
    }

    @Override
    @Transactional
    public AgentHeartbeatResponse heartbeat(String token, AgentHeartbeatRequest request) {
        if (token == null || token.isBlank()) {
            throw new AgentUnauthorizedException();
        }
        PrinterAgent agent = agentRepository.findByTokenHash(tokenService.hash(token.trim()))
                .orElseThrow(AgentUnauthorizedException::new);
        Instant now = Instant.now();
        agent.recordHeartbeat(request.getAgentVersion(), now); // throws 401 when revoked

        // Last report wins if the agent sends the same printer twice.
        Map<String, AgentHeartbeatRequest.PrinterReport> reports = new LinkedHashMap<>();
        if (request.getPrinters() != null) {
            request.getPrinters().forEach(r -> reports.put(r.getOctoprintPrinterId(), r));
        }

        List<String> unknown = new ArrayList<>();
        int accepted = 0;
        for (AgentHeartbeatRequest.PrinterReport report : reports.values()) {
            if (!report.getStatus().isAgentReportable()) {
                throw new IllegalArgumentException("Agents cannot report status " + report.getStatus());
            }
            var printer = printerRepository.findByAgentIdAndOctoprintPrinterId(agent.getId(), report.getOctoprintPrinterId());
            if (printer.isPresent()) {
                printer.get().applyAgentReport(report.getStatus(), now);
                accepted++;
            } else {
                unknown.add(report.getOctoprintPrinterId());
            }
        }

        return AgentHeartbeatResponse.builder()
                .serverTime(now)
                .pollIntervalSeconds(pollIntervalSeconds)
                .acceptedPrinters(accepted)
                .unknownPrinterIds(unknown)
                .build();
    }

    @Override
    @Transactional
    public int markStaleAgentsOffline(Instant threshold) {
        List<PrinterAgent> stale =
                agentRepository.findByStatusAndLastHeartbeatAtBefore(PrinterAgentStatus.ONLINE, threshold);
        for (PrinterAgent agent : stale) {
            agent.markOffline();
            printerRepository.findByAgentId(agent.getId()).forEach(Printer::markOffline);
            log.warn("Printer agent {} missed heartbeats; marked OFFLINE", agent.getId());
        }
        return stale.size();
    }

    private PrinterAgent find(UUID id) {
        return agentRepository.findById(id).orElseThrow(() -> new PrinterAgentNotFoundException(id));
    }
}
