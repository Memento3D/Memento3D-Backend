package com.memento3d.reconstruction.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiJobResponse(
        @JsonProperty("job_id") UUID jobId,
        @JsonProperty("attempt_id") UUID attemptId,
        String status,
        String stage,
        Integer progress,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("completed_at") Instant completedAt,
        @JsonProperty("output_kind") String outputKind,
        @JsonProperty("customization_ready") Boolean customizationReady,
        @JsonProperty("ready_for_machine") Boolean readyForMachine
) {
    /** Source compatibility for legacy/mock providers that do not emit custom metadata. */
    public AiJobResponse(UUID jobId,UUID attemptId,String status,String stage,Integer progress,
            String errorCode,String errorMessage,Instant createdAt,Instant startedAt,Instant completedAt) {
        this(jobId,attemptId,status,stage,progress,errorCode,errorMessage,createdAt,startedAt,completedAt,null,false,false);
    }
}