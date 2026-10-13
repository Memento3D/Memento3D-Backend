package com.memento3d.scans.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanStatusResponse {

    @JsonProperty("scanId")
    private String scanId;

    private String status;

    private Integer progress;

    private String message;
}
