package com.memento3d.printjob.api.request;

import lombok.Data;

@Data
public class PostProcessingChecklistRequest {

    private Boolean washCompleted = false;
    private Boolean supportRemovalCompleted = false;
    private Boolean uvCureCompleted = false;
    private Integer uvCureDurationMinutes = 15;
    private Boolean surfaceFinishingCompleted = false;
    private String notes;
}
