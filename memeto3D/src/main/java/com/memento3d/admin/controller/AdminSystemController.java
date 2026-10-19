package com.memento3d.admin.controller;

import com.memento3d.admin.dto.AdminSystemHealthResponse;
import com.memento3d.admin.service.AdminSystemHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin System Health", description = "Real-time backend infrastructure diagnostics and connectivity health probes")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {

    private final AdminSystemHealthService healthService;

    @GetMapping("/health")
    @Operation(summary = "Probe real-time system health for PostgreSQL, Redis, Reconstruction AI Worker, and Hardware Printer Gateway")
    public ResponseEntity<AdminSystemHealthResponse> getSystemHealth() {
        return ResponseEntity.ok(healthService.checkHealth());
    }
}
