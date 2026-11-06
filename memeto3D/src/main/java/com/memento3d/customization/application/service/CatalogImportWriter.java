package com.memento3d.customization.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.customization.api.response.CatalogImportResponse;
import com.memento3d.customization.domain.model.*;
import com.memento3d.customization.infrastructure.persistence.repository.*;
import com.memento3d.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class CatalogImportWriter {
    private final CustomizationCatalogRepository catalogs;
    private final CustomizationAssetVersionRepository versions;
    private final CustomizationCatalogMemberRepository members;
    private final ObjectMapper mapper;

    @Transactional
    public CatalogImportResponse persistVerified(CatalogManifestLoader.Loaded loaded) {
        var source=loaded.manifest();
        catalogs.ensureDraft(UUID.randomUUID(),source.code(),source.bodyPreset());
        var catalog=catalogs.findByCodeForUpdate(source.code()).orElseThrow();
        if(!catalog.getBodyPreset().equals(source.bodyPreset()) || "ARCHIVED".equals(catalog.getStatus())) throw conflict();
        if("ACTIVE".equals(catalog.getStatus())) {
            if(!loaded.sha256().equals(catalog.getImportSha256())) throw conflict();
            return new CatalogImportResponse(catalog.getCode(),catalog.getStatus(),members.findMembers(catalog.getId()).size(),catalog.getVerifiedObjectCount(),true);
        }
        int order=0;
        for(var asset:source.assets()) {
            String fingerprint;
            try { fingerprint=CatalogManifestLoader.digest((source.bucket()+"\n"+mapper.writeValueAsString(asset)).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            catch(Exception ex) { throw new IllegalStateException("Cannot serialize asset metadata",ex); }
            var version=versions.findByAssetCodeAndVersion(asset.code(),asset.version()).orElse(null);
            if(version!=null) {
                if(!fingerprint.equals(version.getMetadataSha256())) throw conflict();
            } else {
                version=new CustomizationAssetVersion();version.setId(UUID.randomUUID());
                version.setAssetCode(asset.code());version.setVersion(asset.version());version.setName(asset.name());
                version.setKind(asset.kind());version.setSlot(asset.slot());version.setBucket(source.bucket());version.setObjectKey(asset.objectKey());
                version.setFileSizeBytes(asset.bytes());version.setSha256(asset.sha256());
                version.setCloudinaryPublicId(asset.cloudinaryPublicId());version.setCloudinaryVersion(asset.cloudinaryVersion());
                version.setThumbnailUrl(asset.thumbnailUrl());version.setThumbnailSha256(asset.thumbnailSha256());
                version.setPlacementPolicy(asset.placementPolicy());version.setCompatibilityJson(asset.compatibilityJson());
                version.setPrintReady(false);version.setMetadataSha256(fingerprint);versions.save(version);
            }
            var member=new CustomizationCatalogMember();member.setId(UUID.randomUUID());member.setCatalog(catalog);
            member.setAssetVersion(version);member.setSortOrder(order++);members.save(member);
        }
        catalog.setGroupsJson(source.groupsJson());catalog.setDefaultSelectionJson(source.defaultSelectionJson());
        catalog.setImportSha256(loaded.sha256());catalog.setVerifiedObjectCount(source.files().size());
        catalog.setVerifiedAt(Instant.now());catalog.setStatus("ACTIVE");catalogs.saveAndFlush(catalog);
        return new CatalogImportResponse(catalog.getCode(),catalog.getStatus(),source.assets().size(),source.files().size(),false);
    }
    private BusinessException conflict() { return new BusinessException(HttpStatus.CONFLICT,"CATALOG_VERSION_CONFLICT","Catalog or asset version is immutable; use a new version"); }
}
