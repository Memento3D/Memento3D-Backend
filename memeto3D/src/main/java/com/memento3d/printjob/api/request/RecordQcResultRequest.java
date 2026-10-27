package com.memento3d.printjob.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecordQcResultRequest {

    @NotBlank(message = "Trạng thái QC không được để trống (PASSED hoặc REJECTED)")
    private String qcStatus; // PASSED hoặc REJECTED

    private String qcNotes;

    private String failureReasonCode; // WARPING, DELAMINATION, UNDER_EXPOSURE, DETAIL_LOSS, OTHER

    private String failurePhotoUrls;
}
