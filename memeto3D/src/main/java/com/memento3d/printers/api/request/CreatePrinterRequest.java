package com.memento3d.printers.api.request;

import com.memento3d.printers.domain.model.PrinterProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Admin request to register a physical printer. Status is never accepted from the client. */
@Getter
@Setter
@NoArgsConstructor
public class CreatePrinterRequest {

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @NotBlank(message = "serialNumber is required")
    @Size(max = 100, message = "serialNumber must be at most 100 characters")
    private String serialNumber;

    @NotBlank(message = "model is required")
    @Size(max = 100, message = "model must be at most 100 characters")
    private String model;

    @NotNull(message = "provider is required")
    private PrinterProvider provider;

    @Schema(description = "Agent that controls this printer. Must be provided together with octoprintPrinterId")
    private UUID agentId;

    @Size(max = 100, message = "octoprintPrinterId must be at most 100 characters")
    private String octoprintPrinterId;
}
