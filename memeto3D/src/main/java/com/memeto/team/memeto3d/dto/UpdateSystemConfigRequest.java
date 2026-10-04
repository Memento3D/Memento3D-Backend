package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSystemConfigRequest {

    @NotBlank(message = "config_value must not be blank")
    @JsonProperty("config_value")
    private String configValue;

    private String description;
}
