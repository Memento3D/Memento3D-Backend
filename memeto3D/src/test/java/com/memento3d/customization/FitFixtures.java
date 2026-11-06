package com.memento3d.customization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.customization.application.service.*;
import com.memento3d.customization.domain.model.*;
import com.memento3d.reconstruction.application.dto.AiCustomizationManifest;
import java.util.*;

/** Explicit synthetic protocol fixtures; not neural/print-quality evidence. */
final class FitFixtures {
    static final ObjectMapper MAPPER=new ObjectMapper();
    static CatalogManifestLoader.Loaded pins() { return new CatalogManifestLoader(MAPPER).load("mvp-v1"); }
    static byte[] glb(String code) { byte[] b=new byte[32];Arrays.fill(b,(byte)code.hashCode());b[0]='g';b[1]='l';b[2]='T';b[3]='F';return b; }
    static List<Double> identity() { return List.of(1.,0.,0.,0.,0.,1.,0.,0.,0.,0.,1.,0.,0.,0.,0.,1.); }
    static CustomizationCatalog catalog() {
        var pins=pins();var source=pins.manifest();var c=new CustomizationCatalog();c.setId(UUID.randomUUID());c.setCode(source.code());
        c.setBodyPreset(source.bodyPreset());c.setStatus("ACTIVE");c.setImportSha256(pins.sha256());
        c.setGroupsJson(source.groupsJson());c.setDefaultSelectionJson(source.defaultSelectionJson());return c;
    }
    static List<CustomizationAssetVersion> versions() {
        return pins().manifest().assets().stream().map(a->{var v=new CustomizationAssetVersion();v.setId(UUID.randomUUID());
            v.setAssetCode(a.code());v.setVersion(a.version());v.setSha256(a.sha256());v.setBucket(pins().manifest().bucket());
            v.setObjectKey(a.objectKey());v.setFileSizeBytes(a.bytes());v.setPrintReady(false);return v;}).toList();
    }
    static AiCustomizationManifest manifest(UUID job,UUID attempt,String baseSha) throws Exception {
        var pins=pins();var source=pins.manifest();List<AiCustomizationManifest.Asset> assets=new ArrayList<>();
        for(var a:source.assets()) {
            boolean shared=a.code().equals("graduation-cap");
            assets.add(new AiCustomizationManifest.Asset(a.code(),a.version(),shared?"shared_transform":"fitted_geometry",
                shared?a.sha256():CatalogManifestLoader.digest(glb(a.code())),a.sha256(),identity()));
        }
        String catalogSha=pins.manifest().files().stream().filter(p->p.key().endsWith("/catalog.json")).findFirst().orElseThrow().sha256();
        String profileSha=pins.manifest().files().stream().filter(p->p.key().endsWith("/fitting-profile.json")).findFirst().orElseThrow().sha256();
        return new AiCustomizationManifest(1,"mvp_landmark_fit_v1",job,attempt,"mvp-v1",catalogSha,profileSha,
            new AiCustomizationManifest.Base(baseSha,"customization_base"),MAPPER.readValue(source.defaultSelectionJson(),new com.fasterxml.jackson.core.type.TypeReference<>() {}),
            MAPPER.readValue(source.groupsJson(),new com.fasterxml.jackson.core.type.TypeReference<>() {}),assets,
            "glTF Y-up; column-major matrices; one common model root","NEEDS_VISUAL_REVIEW",false);
    }
    static AiCustomizationManifest.Download download(UUID job,UUID attempt,String hash) throws Exception {
        var m=manifest(job,attempt,hash);return new AiCustomizationManifest.Download(MAPPER.writeValueAsString(m),m);
    }
}
