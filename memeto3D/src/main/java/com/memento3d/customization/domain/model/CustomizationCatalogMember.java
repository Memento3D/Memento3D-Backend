package com.memento3d.customization.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity @Table(name="customization_catalog_members",uniqueConstraints=@UniqueConstraint(name="uq_custom_catalog_member",columnNames={"catalog_id","asset_version_id"}))
@Getter @Setter @NoArgsConstructor
public class CustomizationCatalogMember {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="catalog_id",nullable=false) private CustomizationCatalog catalog;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="asset_version_id",nullable=false) private CustomizationAssetVersion assetVersion;
    @Column(name="sort_order",nullable=false) private int sortOrder;
}
