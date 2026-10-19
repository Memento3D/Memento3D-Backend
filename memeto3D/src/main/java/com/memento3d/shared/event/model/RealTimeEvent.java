package com.memento3d.shared.event.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealTimeEvent {
    private UUID eventId;
    private String type;
    private String resourceType;
    private String resourceId;
    private UUID targetUserId;
    private Instant occurredAt;
    private Map<String, Object> data;
}
