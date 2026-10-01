package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Response body for GET /api/v1/uploads/{id}/status.
 *
 * <p>Allows a client to resume a chunked upload after a network interruption:
 * it queries this endpoint, finds the missing_indexes, and only re-transmits
 * those chunks instead of restarting from the beginning.</p>
 *
 * <p>When {@code upload_status} is {@code "completed"}, {@code missing_indexes}
 * will be {@code null} (omitted from JSON).</p>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Upload session status showing which chunks have been received and which are still missing")
public class UploadStatusResponse {

    @Schema(description = "Unique ID of the upload session (scan ID)")
    private UUID id;

    @JsonProperty("file_reference")
    @Schema(description = "Filename or client-generated session key that identifies the file being uploaded")
    private String fileReference;

    @JsonProperty("chunk_total")
    @Schema(description = "Total number of chunks the file was split into", example = "50")
    private Integer chunkTotal;

    @JsonProperty("chunk_uploaded")
    @Schema(description = "Number of chunks successfully received by the server", example = "37")
    private Integer chunkUploaded;

    @JsonProperty("upload_status")
    @Schema(
        description = "Overall upload session status",
        allowableValues = {"in_progress", "completed", "failed", "cancelled"},
        example = "in_progress"
    )
    private String uploadStatus;

    /**
     * Sorted, zero-based list of chunk indexes that the server has NOT yet
     * received (or that failed/were cancelled). The client must re-upload
     * exactly these chunks to complete the transfer.
     *
     * <p>Omitted from the JSON response when the upload is already
     * {@code completed} (no chunks are missing).</p>
     */
    @JsonProperty("missing_indexes")
    @Schema(
        description = "Sorted list of chunk indexes that are still missing. Null when the upload is completed.",
        example = "[12, 15, 20]",
        nullable = true
    )
    private List<Integer> missingIndexes;
}
