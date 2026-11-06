package com.memento3d.customization;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.memento3d.customization.application.dto.CatalogImportManifest;
import com.memento3d.customization.application.service.CatalogManifestLoader;
import com.memento3d.shared.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class CatalogManifestLoaderTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final CatalogManifestLoader loader=new CatalogManifestLoader(mapper);
    @Test void pinnedMvpPackageHasThreeVersionsAndAllFifteenObjects() {
        var loaded=loader.load("mvp-v1");assertEquals(3,loaded.manifest().assets().size());
        assertEquals(15,loaded.manifest().files().size());assertEquals(64,loaded.sha256().length());
        assertEquals("thumbnail_cwgkiz",loaded.manifest().assets().get(0).cloudinaryPublicId());
        assertTrue(loaded.manifest().assets().stream().noneMatch(CatalogImportManifest.Asset::printReady));
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"../mvp-v1","unknown","MVP",""})
    void unknownOrUnsafeCatalogCannotReadOtherResources(String code) {
        var error=assertThrows(BusinessException.class,()->loader.load(code));assertEquals(404,error.getStatus().value());
    }
    static Stream<Arguments> invalidMetadata() {
        return Stream.of(
            Arguments.of("/code","null"),Arguments.of("/bodyPreset","\"\""),Arguments.of("/bucket","\"wrong/bucket\""),
            Arguments.of("/groupsJson","\"{}\""),Arguments.of("/defaultSelectionJson","\"[]\""),
            Arguments.of("/files","[]"),Arguments.of("/files/0/key","\"library/mvp-v1/../other\""),
            Arguments.of("/files/0/bytes","-1"),Arguments.of("/files/0/bytes","52428801"),Arguments.of("/files/0/sha256","\"not-a-sha\""),
            Arguments.of("/assets","[]"),Arguments.of("/assets/0/code","\"../cap\""),Arguments.of("/assets/0/version","null"),
            Arguments.of("/assets/0/name","\" \""),Arguments.of("/assets/0/kind","null"),Arguments.of("/assets/0/slot","\"unknown\""),
            Arguments.of("/assets/0/objectKey","\"library/mvp-v1/unknown.glb\""),Arguments.of("/assets/0/bytes","1"),
            Arguments.of("/assets/0/sha256","\"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff\""),
            Arguments.of("/assets/0/cloudinaryPublicId","\"wrong-thumbnail\""),Arguments.of("/assets/0/cloudinaryVersion","0"),
            Arguments.of("/assets/0/thumbnailUrl","\"https://evil.example/image/upload/v1/x.png\""),
            Arguments.of("/assets/0/thumbnailUrl","\"http://res.cloudinary.com/a.png\""),
            Arguments.of("/assets/0/thumbnailUrl","\"https://res.cloudinary.com/a.png?secret=x\""),
            Arguments.of("/assets/0/placementPolicy","null"),Arguments.of("/assets/0/printReady","true"),
            Arguments.of("/assets/0/compatibilityJson","\"{}\""),Arguments.of("/assets/0/thumbnailSha256","null"));
    }
    @ParameterizedTest @MethodSource("invalidMetadata")
    void invalidPinsCompatibilityAndDeliveryMetadataAreRejected(String pointer,String value) throws Exception {
        ObjectNode tree=mapper.valueToTree(loader.load("mvp-v1").manifest());
        int split=pointer.lastIndexOf('/');((ObjectNode)tree.at(pointer.substring(0,split))).set(pointer.substring(split+1),mapper.readTree(value));
        var invalid=mapper.treeToValue(tree,CatalogImportManifest.class);
        assertThrows(Exception.class,()->loader.validate(invalid));
    }
    @Test void duplicateObjectKeysAndAssetCodesAreRejected() throws Exception {
        ObjectNode tree=mapper.valueToTree(loader.load("mvp-v1").manifest());
        var files=(com.fasterxml.jackson.databind.node.ArrayNode)tree.get("files");files.add(files.get(0).deepCopy());
        var duplicateFiles=mapper.treeToValue(tree,CatalogImportManifest.class);
        assertThrows(Exception.class,()->loader.validate(duplicateFiles));
        tree=mapper.valueToTree(loader.load("mvp-v1").manifest());
        var assets=(com.fasterxml.jackson.databind.node.ArrayNode)tree.get("assets");assets.add(assets.get(0).deepCopy());
        var invalid=mapper.treeToValue(tree,CatalogImportManifest.class);assertThrows(Exception.class,()->loader.validate(invalid));
    }
}
