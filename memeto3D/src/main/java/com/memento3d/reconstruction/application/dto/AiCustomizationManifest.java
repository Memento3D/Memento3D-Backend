package com.memento3d.reconstruction.application.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown=true)
public record AiCustomizationManifest(
    @JsonProperty("schema_version") int schemaVersion,String policy,
    @JsonProperty("job_id") UUID jobId,@JsonProperty("attempt_id") UUID attemptId,
    @JsonProperty("catalog_version") String catalogVersion,@JsonProperty("catalog_sha256") String catalogSha256,
    @JsonProperty("profile_sha256") String profileSha256,@JsonProperty("base_model") Base baseModel,
    @JsonProperty("default_selection") Map<String,String> defaultSelection,List<Map<String,Object>> groups,
    List<Asset> assets,@JsonProperty("coordinate_system") String coordinateSystem,
    @JsonProperty("quality_status") String qualityStatus,@JsonProperty("ready_for_machine") boolean readyForMachine) {
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record Base(String sha256,@JsonProperty("output_kind") String outputKind) {}
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record Asset(@JsonProperty("asset_id") String assetId,@JsonProperty("asset_version") String assetVersion,
        @JsonProperty("geometry_mode") String geometryMode,String sha256,@JsonProperty("source_sha256") String sourceSha256,
        @JsonProperty("asset_to_base_matrix_column_major") List<Double> matrix) {}
    public record Download(String json,AiCustomizationManifest manifest) {}
}
