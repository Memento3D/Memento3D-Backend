package com.memento3d.warehouse.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update warehouse details")
public class UpdateWarehouseRequest {

    @Size(max = 200, message = "Name must not exceed 200 characters")
    @Schema(description = "Facility name", example = "Central Hub District 9 - Expanded")
    private String name;

    @Size(max = 500, message = "Location must not exceed 500 characters")
    @Schema(description = "Physical address or location coordinates", example = "Block A, High-Tech Park, Thu Duc City")
    private String location;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    @Schema(description = "Full physical street address")
    private String address;

    @Schema(description = "Detailed facility description")
    private String description;

    @Schema(description = "Assigned warehouse manager user ID")
    private UUID managerId;
}
