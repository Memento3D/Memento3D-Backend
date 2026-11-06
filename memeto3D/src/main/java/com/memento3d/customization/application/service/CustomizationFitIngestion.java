package com.memento3d.customization.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.assets.domain.port.ModelStoragePort;
import com.memento3d.customization.domain.model.*;
import com.memento3d.customization.infrastructure.persistence.repository.*;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class CustomizationFitIngestion {
    private final AiGenerationClient ai;
    private final ModelStoragePort storage;
    private final CatalogManifestLoader loader;
    private final CustomizationFitValidator validator;
    private final CustomizationCatalogRepository catalogs;
    private final CustomizationCatalogMemberRepository members;
    private final CustomizationFitResultRepository fits;
    private final CustomizationFitAssetRepository assets;
    private final ObjectMapper mapper;

    public record PreparedAsset(UUID versionId,String geometryMode,List<Double> matrix,String sha256,ModelStoragePort.StoredModel file) {}
    public record Prepared(UUID catalogId,String catalogCode,String catalogImportSha256,String baseSha256,
        String manifestSha256,String profileSha256,String policy,String sceneJson,List<PreparedAsset> assets,
        UUID jobId,UUID attemptId,UUID userId,UUID scanId) {}

    /** HTTP and B2 writes run outside the final publication transaction. */
    public Prepared prepare(UUID jobId,UUID attemptId,UUID userId,UUID scanId,String baseSha) {
        if(!"b2".equals(storage.provider()))throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"B2_STORAGE_REQUIRED","Custom scenes require B2 storage");
        var download=ai.getCustomizationManifest(jobId,attemptId);
        if(download==null || download.json()==null)throw mismatch();
        var scene=download.manifest();
        if(scene==null || scene.catalogVersion()==null)throw mismatch();
        var catalog=catalogs.findByCode(scene.catalogVersion()).orElseThrow(()->new BusinessException(HttpStatus.CONFLICT,"CATALOG_NOT_READY","Import the verified asset catalog before finalizing custom scenes"));
        if(!"ACTIVE".equals(catalog.getStatus()))throw new BusinessException(HttpStatus.CONFLICT,"CATALOG_NOT_READY","Catalog is not active");
        var versions=members.findMembers(catalog.getId()).stream().map(CustomizationCatalogMember::getAssetVersion).toList();
        var pins=loader.load(catalog.getCode());validator.validate(scene,jobId,attemptId,baseSha,catalog,pins,versions);
        List<PreparedAsset> prepared=new ArrayList<>();
        for(var entry:scene.assets()) {
            var version=versions.stream().filter(v->v.getAssetCode().equals(entry.assetId())).findFirst().orElseThrow();
            ModelStoragePort.StoredModel file;
            if("shared_transform".equals(entry.geometryMode())) {
                file=new ModelStoragePort.StoredModel("b2",version.getObjectKey(),null,"b2://"+version.getBucket()+"/"+version.getObjectKey(),version.getFileSizeBytes());
            } else {
                byte[] bytes=ai.downloadCustomizationAsset(jobId,attemptId,entry.assetId());
                if(!entry.sha256().equals(CatalogManifestLoader.digest(bytes)))throw mismatch();
                String name="customization/"+entry.assetId()+".glb";
                try { file=storage.uploadGlbArtifact(bytes,userId,scanId,jobId,attemptId,name); }
                catch(java.io.IOException ex) { throw new IllegalStateException("Failed to store fitted accessory",ex); }
                String expected="memento3d/users/"+userId+"/scans/"+scanId+"/jobs/"+jobId+"/attempts/"+attemptId+"/"+name;
                if(file==null || !"b2".equals(file.provider()) || !expected.equals(file.objectKey()) || file.sizeBytes()!=bytes.length
                    || !("b2://"+version.getBucket()+"/"+expected).equals(file.location()))throw mismatch();
            }
            prepared.add(new PreparedAsset(version.getId(),entry.geometryMode(),List.copyOf(entry.matrix()),entry.sha256(),file));
        }
        try { return new Prepared(catalog.getId(),catalog.getCode(),catalog.getImportSha256(),baseSha,
                CatalogManifestLoader.digest(download.json().getBytes(java.nio.charset.StandardCharsets.UTF_8)),scene.profileSha256(),scene.policy(),
                mapper.writeValueAsString(scene),List.copyOf(prepared),jobId,attemptId,userId,scanId); }
        catch(java.io.IOException ex) { throw new IllegalStateException("Cannot serialize fit metadata",ex); }
    }

    /** Must commit atomically with ModelOutput and job.done, never independently. */
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID persist(UUID jobId,UUID attemptId,UUID userId,UUID scanId,Prepared prepared) {
        if(!jobId.equals(prepared.jobId()) || !attemptId.equals(prepared.attemptId())
            || !userId.equals(prepared.userId()) || !scanId.equals(prepared.scanId()))throw mismatch();
        var catalog=catalogs.findByCodeForUpdate(prepared.catalogCode()).orElseThrow(this::mismatch);
        if(!catalog.getId().equals(prepared.catalogId()) || !"ACTIVE".equals(catalog.getStatus())
            || !Objects.equals(catalog.getImportSha256(),prepared.catalogImportSha256()))throw mismatch();
        var existing=fits.findByJobIdAndAttemptId(jobId,attemptId).orElse(null);
        if(existing!=null) {
            if(!existing.getManifestSha256().equals(prepared.manifestSha256()) || !existing.getBaseSha256().equals(prepared.baseSha256())
                || !existing.getUserId().equals(userId) || !existing.getScanId().equals(scanId))throw mismatch();
            return existing.getId();
        }
        var fit=new CustomizationFitResult();fit.setId(UUID.randomUUID());fit.setJobId(jobId);fit.setAttemptId(attemptId);
        fit.setUserId(userId);fit.setScanId(scanId);fit.setCatalogId(prepared.catalogId());fit.setPolicy(prepared.policy());
        fit.setBaseSha256(prepared.baseSha256());fit.setManifestSha256(prepared.manifestSha256());fit.setProfileSha256(prepared.profileSha256());fit.setSceneJson(prepared.sceneJson());fits.save(fit);
        for(var entry:prepared.assets()) {
            var a=new CustomizationFitAsset();a.setId(UUID.randomUUID());a.setFitResultId(fit.getId());a.setAssetVersionId(entry.versionId());
            a.setGeometryMode(entry.geometryMode());a.setStorageProvider(entry.file().provider());a.setObjectKey(entry.file().objectKey());
            a.setLocation(entry.file().location());a.setFileSizeBytes(entry.file().sizeBytes());a.setSha256(entry.sha256());
            try { a.setMatrixJson(mapper.writeValueAsString(entry.matrix())); }
            catch(java.io.IOException ex) { throw new IllegalStateException("Cannot serialize fit transform",ex); }
            assets.save(a);
        }
        return fit.getId();
    }
    private BusinessException mismatch() { return new BusinessException(HttpStatus.CONFLICT,"AI_CUSTOMIZATION_MISMATCH","Custom scene checksum or ownership mismatch"); }
}
