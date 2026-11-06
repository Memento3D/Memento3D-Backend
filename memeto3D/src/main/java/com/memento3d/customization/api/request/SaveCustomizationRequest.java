package com.memento3d.customization.api.request;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

@Schema(description="Save the complete selection map, using asset VERSION UUIDs from the scene. No model URL or transform is accepted.")
public record SaveCustomizationRequest(
    @NotNull UUID fitResultId,
    @NotNull @PositiveOrZero @Schema(description="0 before first save; otherwise the revision returned by the server") Long revision,
    @NotNull @Size(min=1,max=20) Map<@Pattern(regexp="[a-z][a-z0-9_-]{0,39}") String,UUID> selection) {}
