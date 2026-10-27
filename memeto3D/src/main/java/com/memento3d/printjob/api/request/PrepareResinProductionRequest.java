package com.memento3d.printjob.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrepareResinProductionRequest {

    @NotNull(message = "Chiều rộng (X mm) không được để trống")
    private BigDecimal dimXMm;

    @NotNull(message = "Chiều dài (Y mm) không được để trống")
    private BigDecimal dimYMm;

    @NotNull(message = "Chiều cao (Z mm) không được để trống")
    private BigDecimal dimZMm;

    private Double scale = 1.0;

    private Boolean isMeshWatertight = true;
    private Boolean isHollowed = false;
    private Boolean hasDrainHoles = false;
    private String supportDensity = "MEDIUM";
}
