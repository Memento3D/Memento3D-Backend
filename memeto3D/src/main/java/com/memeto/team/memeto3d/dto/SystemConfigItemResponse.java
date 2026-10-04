package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memeto.team.memeto3d.entity.SystemConfig;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfigItemResponse {

    private UUID id;

    @JsonProperty("config_key")
    private String configKey;

    @JsonProperty("config_value")
    private String configValue;

    @JsonProperty("value_type")
    private String valueType;

    private String description;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public static SystemConfigItemResponse fromEntity(SystemConfig entity) {
        return SystemConfigItemResponse.builder()
                .id(entity.getId())
                .configKey(entity.getConfigKey())
                .configValue(entity.getConfigValue())
                .valueType(entity.getValueType())
                .description(entity.getDescription())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
