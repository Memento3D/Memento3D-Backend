package com.memento3d.shared.application.service;

import com.memento3d.shared.domain.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuditLogService {
    void log(UUID actorId, String action, String entityType, String entityId, String beforeData, String afterData);
    Page<AuditLog> getAuditLogs(UUID actorId, String action, String entityType, Pageable pageable);
}
