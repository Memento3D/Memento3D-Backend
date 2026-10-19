package com.memento3d.identity.api;

import com.memento3d.identity.api.response.LegalDocumentResponse;
import com.memento3d.identity.application.service.AuthService;
import com.memento3d.shared.api.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/legal", "/api/legal"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Legal Documents", description = "Public endpoints for Terms of Service and Privacy Policy")
public class LegalController {

    private final AuthService authService;

    @GetMapping("/terms/current")
    @Operation(summary = "Get current active Terms of Service", description = "Retrieves the latest published version of Memento3D Terms of Service")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Terms of Service retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LegalDocumentResponse.class))),
            @ApiResponse(responseCode = "404", description = "No active Terms of Service found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LegalDocumentResponse> getCurrentTerms() {
        log.info("Fetching current active Terms of Service");
        LegalDocumentResponse response = authService.getCurrentLegalDocument("TERMS");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/privacy/current")
    @Operation(summary = "Get current active Privacy Policy", description = "Retrieves the latest published version of Memento3D Privacy Policy")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Privacy Policy retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LegalDocumentResponse.class))),
            @ApiResponse(responseCode = "404", description = "No active Privacy Policy found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LegalDocumentResponse> getCurrentPrivacyPolicy() {
        log.info("Fetching current active Privacy Policy");
        LegalDocumentResponse response = authService.getCurrentLegalDocument("PRIVACY");
        return ResponseEntity.ok(response);
    }
}
