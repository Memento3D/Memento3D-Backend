package com.memento3d.warehouse.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to register a new warehouse facility")
public class CreateWarehouseRequest {

    @NotBlank(message = "Warehouse code is required")
    @Size(max = 50, message = "Code must not exceed 50 characters")
    @Schema(description = "Unique warehouse code", example = "WH-HCM-01")
    private String code;

    @NotBlank(message = "Warehouse name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    @Schema(description = "Facility name", example = "Central Hub District 9")
    private String name;

    @Size(max = 500, message = "Location must not exceed 500 characters")
    @Schema(description = "Physical address or location coordinates", example = "High-Tech Park, Thu Duc City, HCMC")
    private String location;
}
