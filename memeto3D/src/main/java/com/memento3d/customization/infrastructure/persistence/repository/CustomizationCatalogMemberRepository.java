package com.memento3d.customization.infrastructure.persistence.repository;

import com.memento3d.customization.domain.model.CustomizationCatalogMember;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CustomizationCatalogMemberRepository extends JpaRepository<CustomizationCatalogMember,UUID> {
    @Query("select m from CustomizationCatalogMember m join fetch m.assetVersion where m.catalog.id=:id order by m.sortOrder, m.id")
    List<CustomizationCatalogMember> findMembers(@Param("id") UUID catalogId);
}
