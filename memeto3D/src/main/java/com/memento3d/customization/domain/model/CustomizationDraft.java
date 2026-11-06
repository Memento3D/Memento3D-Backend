package com.memento3d.customization.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="customization_drafts",uniqueConstraints=@UniqueConstraint(name="uq_customization_draft_output",columnNames="model_output_id"))
@Getter @Setter @NoArgsConstructor
public class CustomizationDraft {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="model_output_id",nullable=false,updatable=false) private UUID modelOutputId;
    @Column(name="fit_result_id",nullable=false,updatable=false) private UUID fitResultId;
    @Column(name="user_id",nullable=false,updatable=false) private UUID userId;
    // Writers lock the existing base output, including the first save, before comparing/incrementing revision.
    @Column(nullable=false) private long revision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="selection_json",nullable=false,columnDefinition="jsonb") private String selectionJson;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
}
