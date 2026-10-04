package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupedSystemConfigResponse {

    @JsonProperty("total_configs")
    private Integer totalConfigs;

    private Map<String, List<SystemConfigItemResponse>> groups;
}
