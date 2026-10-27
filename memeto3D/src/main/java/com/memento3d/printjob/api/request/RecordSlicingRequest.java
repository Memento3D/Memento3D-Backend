package com.memento3d.printjob.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecordSlicingRequest {

    private String slicerName = "CHITUBOX";

    @NotBlank(message = "Phiên bản CHITUBOX không được để trống")
    private String slicerVersion; // e.g. "CHITUBOX Basic v2.2.0"

    private String printerProfile = "ELEGOO Saturn 4 Ultra 16K";

    @NotBlank(message = "Định dạng file cắt lớp không được để trống (GOO hoặc CTB)")
    private String sliceFileFormat; // "GOO" hoặc "CTB"

    @NotBlank(message = "URL file cắt lớp (.goo/.ctb) không được để trống")
    private String sliceFileUrl;

    private String sliceFileSha256;

    private String previewImageUrl;

    @NotBlank(message = "Loại resin không được để trống")
    private String resinType; // e.g. "Standard 16K Resin", "ABS-Like", "Water Washable"

    @NotBlank(message = "Màu sắc resin không được để trống")
    private String resinColor; // e.g. "Space Grey", "Clear", "Beige"

    private Boolean isCustomPainted = false; // Phân biệt rõ màu resin gốc và dịch vụ sơn/hoàn thiện màu

    @NotNull(message = "Lượng resin ước tính (ml) không được để trống")
    private BigDecimal estimatedResinMl;

    @NotNull(message = "Thời gian in ước tính (giây) không được để trống")
    private Integer estimatedPrintTimeSeconds;
}
