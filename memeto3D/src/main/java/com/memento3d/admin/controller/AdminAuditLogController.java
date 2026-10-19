package com.memento3d.admin.controller;

import com.memento3d.shared.api.response.AuditLogResponse;
import com.memento3d.shared.application.service.AuditLogService;
import com.memento3d.shared.domain.model.AuditLog;
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
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin System Audit", description = "Query immutable system audit log entries and historical administrative actions")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("")
    @Operation(summary = "Query immutable system audit trail with actor, action, and entity type filters")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @RequestParam(name = "actorId", required = false) UUID actorId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "entityType", required = false) String entityType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> auditLogs = auditLogService.getAuditLogs(actorId, action, entityType, pageRequest);
        return ResponseEntity.ok(auditLogs.map(this::mapAuditLog));
    }

    private AuditLogResponse mapAuditLog(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .actorId(log.getActorId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .beforeData(log.getBeforeData())
                .afterData(log.getAfterData())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
