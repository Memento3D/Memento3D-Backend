package com.memento3d.customization.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.customization.api.response.*;
import com.memento3d.customization.domain.port.CatalogObjectVerifier;
import com.memento3d.customization.infrastructure.persistence.repository.*;
import com.memento3d.models.application.service.ModelDeliveryService;
import com.memento3d.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AssetCatalogService {
    private final CatalogManifestLoader loader;
    private final CatalogObjectVerifier verifier;
    private final CatalogImportWriter writer;
    private final CustomizationCatalogRepository catalogs;
    private final CustomizationCatalogMemberRepository members;
    private final ModelDeliveryService delivery;
    private final ObjectMapper mapper;

    // External reads happen before opening the write transaction.
    public CatalogImportResponse importCatalog(String code) {
        var loaded=loader.load(code);var source=loaded.manifest();
        for(var file:source.files()) verifier.verify(source.bucket(),file.key(),file.bytes(),file.sha256());
        return writer.persistVerified(loaded);
    }
    @Transactional(readOnly=true)
    public CustomizationCatalogResponse getCatalog(String code) {
        var catalog=catalogs.findByCode(code).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"CATALOG_NOT_FOUND","Catalog not found"));
        if(!"ACTIVE".equals(catalog.getStatus())) throw new BusinessException(HttpStatus.CONFLICT,"CATALOG_NOT_READY","Catalog is not active");
        var assets=members.findMembers(catalog.getId()).stream().map(member->{
            var a=member.getAssetVersion();
            String url=delivery.url("b2",a.getObjectKey(),"b2://"+a.getBucket()+"/"+a.getObjectKey());
            return new CustomizationCatalogResponse.Asset(a.getId(),a.getAssetCode(),a.getVersion(),a.getName(),a.getKind(),a.getSlot(),
                url,a.getFileSizeBytes(),a.getSha256(),a.getThumbnailUrl(),a.getPlacementPolicy(),object(a.getCompatibilityJson()),a.isPrintReady());
        }).toList();
        return new CustomizationCatalogResponse(catalog.getCode(),catalog.getBodyPreset(),catalog.getStatus(),
            groups(catalog.getGroupsJson()),object(catalog.getDefaultSelectionJson()),assets,delivery.urlTtlSeconds());
    }
    private java.util.Map<String,Object> object(String source) {
        try { return mapper.readValue(source,new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
        catch(Exception ex) { throw new IllegalStateException("Invalid persisted catalog JSON",ex); }
    }
    private java.util.List<java.util.Map<String,Object>> groups(String source) {
        try { return mapper.readValue(source,new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
        catch(Exception ex) { throw new IllegalStateException("Invalid persisted catalog JSON",ex); }
    }
}
