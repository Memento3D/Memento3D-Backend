package com.memento3d.customization.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.customization.domain.model.*;
import com.memento3d.reconstruction.application.dto.AiCustomizationManifest;
import com.memento3d.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class CustomizationFitValidator {
    private final ObjectMapper mapper;
    public void validate(AiCustomizationManifest scene,UUID jobId,UUID attemptId,String baseSha,
            CustomizationCatalog catalog,CatalogManifestLoader.Loaded pins,List<CustomizationAssetVersion> versions) {
        require(scene!=null && scene.schemaVersion()==1 && "mvp_landmark_fit_v1".equals(scene.policy()));
        require(jobId.equals(scene.jobId()) && attemptId.equals(scene.attemptId()));
        require(scene.baseModel()!=null && baseSha.equals(scene.baseModel().sha256()) && "customization_base".equals(scene.baseModel().outputKind()));
        require(!scene.readyForMachine() && "NEEDS_VISUAL_REVIEW".equals(scene.qualityStatus()));
        require("glTF Y-up; column-major matrices; one common model root".equals(scene.coordinateSystem()));
        require(catalog.getCode().equals(scene.catalogVersion()) && pins.manifest().code().equals(scene.catalogVersion()));
        require(pin(pins,"catalog.json").equals(scene.catalogSha256()) && pin(pins,"fitting-profile.json").equals(scene.profileSha256()));
        try {
            require(mapper.readTree(catalog.getGroupsJson()).equals(mapper.valueToTree(scene.groups())));
            require(mapper.readTree(catalog.getDefaultSelectionJson()).equals(mapper.valueToTree(scene.defaultSelection())));
        } catch(java.io.IOException ex) { throw mismatch(); }
        require(scene.assets()!=null && scene.assets().size()==versions.size() && !versions.isEmpty());
        Set<String> seen=new HashSet<>();
        for(var entry:scene.assets()) {
            require(entry.assetId()!=null && seen.add(entry.assetId()));
            var version=versions.stream().filter(v->v.getAssetCode().equals(entry.assetId()) && v.getVersion().equals(entry.assetVersion())).findFirst().orElseThrow(this::mismatch);
            require(version.getSha256().equals(entry.sourceSha256()) && entry.sha256()!=null && entry.sha256().matches("[0-9a-f]{64}"));
            require(Set.of("shared_transform","fitted_geometry").contains(entry.geometryMode()==null?"":entry.geometryMode()));
            require(!version.isPrintReady());
            if("shared_transform".equals(entry.geometryMode())) require(version.getSha256().equals(entry.sha256()));
            var m=entry.matrix();require(m!=null && m.size()==16);
            for(Double value:m)require(value!=null && Double.isFinite(value) && Math.abs(value)<=10);
            require(Math.abs(m.get(3))<1e-8 && Math.abs(m.get(7))<1e-8 && Math.abs(m.get(11))<1e-8 && Math.abs(m.get(15)-1)<1e-8);
            double det=m.get(0)*(m.get(5)*m.get(10)-m.get(9)*m.get(6))-m.get(4)*(m.get(1)*m.get(10)-m.get(9)*m.get(2))+m.get(8)*(m.get(1)*m.get(6)-m.get(5)*m.get(2));
            require(det>1e-6 && det<8);
            if("fitted_geometry".equals(entry.geometryMode()))
                for(int i=0;i<16;i++)require(Math.abs(m.get(i)-(i%5==0?1:0))<1e-8);
        }
    }
    private String pin(CatalogManifestLoader.Loaded pins,String name) {
        return pins.manifest().files().stream().filter(p->p.key().equals("library/"+pins.manifest().code()+"/"+name)).findFirst().orElseThrow(this::mismatch).sha256();
    }
    private void require(boolean value) { if(!value)throw mismatch(); }
    private BusinessException mismatch() { return new BusinessException(HttpStatus.CONFLICT,"AI_CUSTOMIZATION_MISMATCH","AI customization does not match the job, base model or pinned catalog"); }
}
