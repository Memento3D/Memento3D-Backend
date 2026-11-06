package com.memento3d.customization.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.customization.application.dto.CatalogImportManifest;
import com.memento3d.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.security.*;
import java.util.*;

@Component @RequiredArgsConstructor
public class CatalogManifestLoader {
    private final ObjectMapper mapper;
    public record Loaded(CatalogImportManifest manifest,String sha256) {}
    public Loaded load(String code) {
        if (code==null || !code.matches("[a-z0-9][a-z0-9-]{0,99}")) throw new BusinessException(HttpStatus.NOT_FOUND,"CATALOG_NOT_FOUND","Unknown built-in catalog");
        var resource=new ClassPathResource("customization/"+code+"-import.json");
        if (!resource.exists()) throw new BusinessException(HttpStatus.NOT_FOUND,"CATALOG_NOT_FOUND","Unknown built-in catalog");
        try(var stream=resource.getInputStream()) {
            byte[] bytes=stream.readAllBytes();
            var manifest=mapper.readValue(bytes,CatalogImportManifest.class);
            validate(manifest);
            if (!code.equals(manifest.code())) throw new IllegalArgumentException("Catalog code mismatch");
            return new Loaded(manifest,digest(bytes));
        } catch(Exception ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"INVALID_CATALOG_MANIFEST","Built-in catalog manifest is invalid");
        }
    }
    public void validate(CatalogImportManifest manifest) throws Exception {
        Objects.requireNonNull(manifest);
        require(manifest.code()!=null && manifest.code().matches("[a-z0-9][a-z0-9-]{0,99}"));
        require(manifest.bodyPreset()!=null && manifest.bodyPreset().matches("[a-z0-9-]{1,100}"));
        require(manifest.bucket()!=null && manifest.bucket().matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"));
        var groups=mapper.readTree(manifest.groupsJson());var selection=mapper.readTree(manifest.defaultSelectionJson());
        require(groups.isArray() && !groups.isEmpty() && selection.isObject());
        require(manifest.files()!=null && !manifest.files().isEmpty() && manifest.files().size()<=1000);
        Map<String,CatalogImportManifest.ObjectPin> pins=new HashMap<>();
        for(var file:manifest.files()) {
            require(file.key()!=null && file.key().startsWith("library/"+manifest.code()+"/") && file.key().length()<=500
                    && !file.key().contains("..") && !file.key().contains("\\") && !file.key().contains("//"));
            require(file.bytes()>0 && file.bytes()<=50L*1024*1024 && validSha(file.sha256()));
            require(pins.put(file.key(),file)==null);
        }
        require(manifest.assets()!=null && !manifest.assets().isEmpty() && manifest.assets().size()<=100);
        Set<String> codes=new HashSet<>();
        for(var asset:manifest.assets()) {
            require(asset.code()!=null && asset.code().matches("[a-z0-9][a-z0-9-]{0,99}") && codes.add(asset.code()));
            require(asset.version()!=null && asset.version().matches("[a-zA-Z0-9_-]{1,40}"));
            require(asset.name()!=null && !asset.name().isBlank() && asset.name().length()<=255);
            require(asset.kind()!=null && asset.kind().matches("[a-z-]{1,40}") && asset.slot()!=null && asset.slot().matches("[a-z-]{1,40}"));
            var pin=pins.get(asset.objectKey());
            require(pin!=null && asset.objectKey().endsWith("/preview.glb") && pin.bytes()==asset.bytes() && pin.sha256().equals(asset.sha256()));
            require(validSha(asset.thumbnailSha256()) && asset.cloudinaryVersion()>0);
            require(asset.cloudinaryPublicId()!=null && !asset.cloudinaryPublicId().isBlank() && asset.cloudinaryPublicId().length()<=500);
            URI url=URI.create(asset.thumbnailUrl());
            require("https".equals(url.getScheme()) && "res.cloudinary.com".equals(url.getHost()) && url.getPort()==-1
                    && url.getUserInfo()==null && url.getQuery()==null && url.getFragment()==null);
            require(url.getPath().contains("/image/upload/v"+asset.cloudinaryVersion()+"/")
                    && url.getPath().endsWith("/"+asset.cloudinaryPublicId()+".png"));
            require(asset.placementPolicy()!=null && asset.placementPolicy().matches("[a-z0-9_]{1,100}") && !asset.printReady());
            var compatibility=mapper.readTree(asset.compatibilityJson());
            require(compatibility.isObject() && compatibility.path("bodyPreset").asText().equals(manifest.bodyPreset())
                    && compatibility.path("requiresPerJobFit").asBoolean());
        }
        Set<String> grouped=new HashSet<>();
        for(var group:groups) {
            require(group.path("id").isTextual() && group.path("assets").isArray());
            for(var asset:group.path("assets")) {
                require(asset.isTextual() && codes.contains(asset.asText()) && grouped.add(asset.asText()));
                require(manifest.assets().stream().anyMatch(a->a.code().equals(asset.asText()) && a.slot().equals(group.path("id").asText())));
            }
            var choice=selection.get(group.path("id").asText());
            if(group.path("required").asBoolean()) require(choice!=null && choice.isTextual());
            if(choice!=null && !choice.isNull()) {
                require(choice.isTextual());
                boolean found=false;for(var a:group.path("assets")) if(a.asText().equals(choice.asText())) found=true;
                require(found);
            }
        }
        require(grouped.equals(codes));
    }
    private static void require(boolean value) { if(!value) throw new IllegalArgumentException("Invalid catalog metadata"); }
    private static boolean validSha(String value) { return value!=null && value.matches("[0-9a-f]{64}"); }
    public static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
