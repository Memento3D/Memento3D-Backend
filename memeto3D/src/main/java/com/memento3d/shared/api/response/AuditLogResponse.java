package com.memento3d.shared.api.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {
    private UUID id;
    private UUID actorId;
    private String action;
    private String entityType;
    private String entityId;
    private String beforeData;
    private String afterData;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
}
