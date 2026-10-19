package com.memento3d.printjob.api.response;

import com.memento3d.printjob.domain.model.PrintJob;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Production Print Job details")
public class PrintJobResponse {

    private UUID id;
    private String jobNumber;
    private UUID orderId;
    private UUID orderItemId;
    private UUID modelVersionId;
    private boolean isOutsource;
    private String partnerName;
    private UUID printerId;
    private String printerName;
    private String printerModel;
    private UUID materialId;
    private String materialSku;
    private String materialName;
    private String materialColor;
    private PrintJobStatus status;
    private BigDecimal estimatedGram;
    private Integer estimatedDurationSeconds;
    private Instant startedAt;
    private Instant endedAt;
    private String failureReason;
    private UUID createdBy;
    private Instant createdAt;

    public static PrintJobResponse fromEntity(PrintJob entity) {
        if (entity == null) return null;
        return PrintJobResponse.builder()
                .id(entity.getId())
                .jobNumber(entity.getJobNumber())
                .orderId(entity.getOrderId())
                .orderItemId(entity.getOrderItemId())
                .modelVersionId(entity.getModelVersionId())
                .isOutsource(entity.isOutsource())
                .partnerName(entity.getPartnerName())
                .printerId(entity.getPrinter() != null ? entity.getPrinter().getId() : null)
                .printerName(entity.getPrinter() != null ? entity.getPrinter().getName() : null)
                .printerModel(entity.getPrinter() != null ? entity.getPrinter().getModel() : null)
                .materialId(entity.getMaterial() != null ? entity.getMaterial().getId() : null)
                .materialSku(entity.getMaterial() != null ? entity.getMaterial().getSku() : null)
                .materialName(entity.getMaterial() != null ? entity.getMaterial().getName() : null)
                .materialColor(entity.getMaterial() != null ? entity.getMaterial().getColor() : null)
                .status(entity.getStatus())
                .estimatedGram(entity.getEstimatedGram())
                .estimatedDurationSeconds(entity.getEstimatedDurationSeconds())
                .startedAt(entity.getStartedAt())
                .endedAt(entity.getEndedAt())
                .failureReason(entity.getFailureReason())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
