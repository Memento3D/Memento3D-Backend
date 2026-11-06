package com.memento3d.customization.infrastructure.persistence.repository;

import com.memento3d.customization.domain.model.CustomizationCatalog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CustomizationCatalogRepository extends JpaRepository<CustomizationCatalog,UUID> {
    Optional<CustomizationCatalog> findByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomizationCatalog c where c.code=:code")
    Optional<CustomizationCatalog> findByCodeForUpdate(@Param("code") String code);
    @Modifying
    @Query(value="insert into public.customization_catalogs(id,code,body_preset,status) values(:id,:code,:body,'DRAFT') on conflict(code) do nothing",nativeQuery=true)
    void ensureDraft(@Param("id") UUID id,@Param("code") String code,@Param("body") String bodyPreset);
}
