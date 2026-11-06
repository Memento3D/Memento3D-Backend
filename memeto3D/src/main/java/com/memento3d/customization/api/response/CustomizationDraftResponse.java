package com.memento3d.customization.api.response;
import java.time.Instant;
import java.util.*;

public record CustomizationDraftResponse(UUID modelOutputId,UUID fitResultId,boolean exists,long revision,
    Map<String,UUID> selection,boolean editable,Instant updatedAt) {}
