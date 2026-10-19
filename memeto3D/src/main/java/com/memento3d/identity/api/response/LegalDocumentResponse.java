package com.memento3d.identity.api.response;

import com.memento3d.identity.domain.model.LegalDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Legal document details response")
public class LegalDocumentResponse {

    @Schema(description = "Document identifier")
    private UUID id;

    @Schema(description = "Document type", example = "TERMS")
    private String type;

    @Schema(description = "Document version", example = "1.0")
    private String version;

    @Schema(description = "Document title", example = "Memento3D Terms of Service")
    private String title;

    @Schema(description = "Markdown content of the document")
    private String content;

    @Schema(description = "Effective timestamp")
    private Instant effectiveAt;

    public static LegalDocumentResponse fromEntity(LegalDocument doc) {
        if (doc == null) return null;
        return LegalDocumentResponse.builder()
                .id(doc.getId())
                .type(doc.getType())
                .version(doc.getVersion())
                .title(doc.getTitle())
                .content(doc.getContent())
                .effectiveAt(doc.getEffectiveAt())
                .build();
    }
}
