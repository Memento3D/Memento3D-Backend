package com.memento3d.printers.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/** Returned only on registration / token rotation. The plaintext token cannot be retrieved later. */
@Getter
@Builder
public class PrinterAgentCredentialResponse {
    private PrinterAgentResponse agent;

    @Schema(description = "Plaintext agent token, shown ONCE. Send as header X-Agent-Token")
    private String token;
}
