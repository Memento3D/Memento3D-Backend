package com.memento3d.printers.application.service.impl;

import com.memento3d.printers.api.request.AgentHeartbeatRequest;
import com.memento3d.printers.api.request.RegisterPrinterAgentRequest;
import com.memento3d.printers.api.response.AgentHeartbeatResponse;
import com.memento3d.printers.api.response.PrinterAgentCredentialResponse;
import com.memento3d.printers.application.mapper.PrinterMapper;
import com.memento3d.printers.domain.exception.AgentUnauthorizedException;
import com.memento3d.printers.domain.exception.PrinterConflictException;
import com.memento3d.printers.domain.model.*;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterAgentRepository;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.printers.infrastructure.security.AgentTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrinterAgentServiceImplTest {

    @Mock PrinterAgentRepository agentRepository;
    @Mock PrinterRepository printerRepository;

    private final AgentTokenService tokenService = new AgentTokenService();
    private PrinterAgentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrinterAgentServiceImpl(agentRepository, printerRepository, tokenService, new PrinterMapper());
    }

    private AgentHeartbeatRequest heartbeat(String remoteId, PrinterStatus status) {
        AgentHeartbeatRequest.PrinterReport r = new AgentHeartbeatRequest.PrinterReport();
        r.setOctoprintPrinterId(remoteId);
        r.setStatus(status);
        AgentHeartbeatRequest req = new AgentHeartbeatRequest();
        req.setAgentVersion("1.0.0");
        req.setPrinters(List.of(r));
        return req;
    }

    @Test
    void register_returnsPlainTokenOnceAndStoresOnlyHash() {
        RegisterPrinterAgentRequest req = new RegisterPrinterAgentRequest();
        req.setName("pi-01");
        when(agentRepository.existsByNameIgnoreCase("pi-01")).thenReturn(false);
        when(agentRepository.saveAndFlush(any(PrinterAgent.class))).thenAnswer(i -> i.getArgument(0));

        PrinterAgentCredentialResponse res = service.register(req, UUID.randomUUID());

        assertTrue(res.getToken().startsWith("m3d_agt_"));
        verify(agentRepository).saveAndFlush(argThat(a ->
                a.getTokenHash().equals(tokenService.hash(res.getToken())) && !a.getTokenHash().equals(res.getToken())));
    }

    @Test
    void register_duplicateName_conflict() {
        RegisterPrinterAgentRequest req = new RegisterPrinterAgentRequest();
        req.setName("pi-01");
        when(agentRepository.existsByNameIgnoreCase("pi-01")).thenReturn(true);
        assertThrows(PrinterConflictException.class, () -> service.register(req, UUID.randomUUID()));
    }

    @Test
    void heartbeat_unknownToken_unauthorized() {
        when(agentRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThrows(AgentUnauthorizedException.class, () -> service.heartbeat("bad", heartbeat("a", PrinterStatus.IDLE)));
        assertThrows(AgentUnauthorizedException.class, () -> service.heartbeat(null, heartbeat("a", PrinterStatus.IDLE)));
    }

    @Test
    void heartbeat_revokedAgent_unauthorized() {
        PrinterAgent agent = PrinterAgent.builder().id(UUID.randomUUID()).status(PrinterAgentStatus.REVOKED).build();
        when(agentRepository.findByTokenHash(any())).thenReturn(Optional.of(agent));
        assertThrows(AgentUnauthorizedException.class, () -> service.heartbeat("tok", heartbeat("a", PrinterStatus.IDLE)));
    }

    @Test
    void heartbeat_updatesAgentAndKnownPrinter_reportsUnknown() {
        PrinterAgent agent = PrinterAgent.builder().id(UUID.randomUUID()).status(PrinterAgentStatus.OFFLINE).build();
        Printer printer = Printer.builder().status(PrinterStatus.OFFLINE).build();
        when(agentRepository.findByTokenHash(any())).thenReturn(Optional.of(agent));
        when(printerRepository.findByAgentIdAndOctoprintPrinterId(agent.getId(), "known")).thenReturn(Optional.of(printer));
        when(printerRepository.findByAgentIdAndOctoprintPrinterId(agent.getId(), "ghost")).thenReturn(Optional.empty());

        AgentHeartbeatRequest req = heartbeat("known", PrinterStatus.IDLE);
        AgentHeartbeatRequest.PrinterReport ghost = new AgentHeartbeatRequest.PrinterReport();
        ghost.setOctoprintPrinterId("ghost");
        ghost.setStatus(PrinterStatus.IDLE);
        req.setPrinters(List.of(req.getPrinters().get(0), ghost));

        AgentHeartbeatResponse res = service.heartbeat("tok", req);

        assertEquals(PrinterAgentStatus.ONLINE, agent.getStatus());
        assertEquals("1.0.0", agent.getAgentVersion());
        assertEquals(PrinterStatus.IDLE, printer.getStatus());
        assertEquals(1, res.getAcceptedPrinters());
        assertEquals(List.of("ghost"), res.getUnknownPrinterIds());
    }

    @Test
    void heartbeat_agentCannotReportMaintenance() {
        PrinterAgent agent = PrinterAgent.builder().id(UUID.randomUUID()).build();
        when(agentRepository.findByTokenHash(any())).thenReturn(Optional.of(agent));
        assertThrows(IllegalArgumentException.class,
                () -> service.heartbeat("tok", heartbeat("a", PrinterStatus.MAINTENANCE)));
    }

    @Test
    void staleAgents_goOfflineWithTheirPrinters() {
        PrinterAgent agent = PrinterAgent.builder().id(UUID.randomUUID()).status(PrinterAgentStatus.ONLINE).build();
        Printer printing = Printer.builder().status(PrinterStatus.PRINTING).build();
        Printer maintenance = Printer.builder().status(PrinterStatus.MAINTENANCE).build();
        when(agentRepository.findByStatusAndLastHeartbeatAtBefore(eq(PrinterAgentStatus.ONLINE), any(Instant.class)))
                .thenReturn(List.of(agent));
        when(printerRepository.findByAgentId(agent.getId())).thenReturn(List.of(printing, maintenance));

        int count = service.markStaleAgentsOffline(Instant.now());

        assertEquals(1, count);
        assertEquals(PrinterAgentStatus.OFFLINE, agent.getStatus());
        assertEquals(PrinterStatus.OFFLINE, printing.getStatus());
        assertEquals(PrinterStatus.MAINTENANCE, maintenance.getStatus());
    }
}
