package com.memento3d.customization;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.memento3d.customization.application.service.CustomizationFitValidator;
import com.memento3d.reconstruction.application.dto.AiCustomizationManifest;
import com.memento3d.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class CustomizationFitValidatorTest {
    final UUID job=UUID.randomUUID(),attempt=UUID.randomUUID();final String hash="a".repeat(64);
    final CustomizationFitValidator validator=new CustomizationFitValidator(FitFixtures.MAPPER);
    void check(AiCustomizationManifest scene) { validator.validate(scene,job,attempt,hash,FitFixtures.catalog(),FitFixtures.pins(),FitFixtures.versions()); }
    @Test void realProtocolShapeAcceptsSharedCapAndFittedHairGlasses() throws Exception { assertDoesNotThrow(()->check(FitFixtures.manifest(job,attempt,hash))); }
    static Stream<Arguments> corruptions() {
        return Stream.of(
            Arguments.of("/schema_version","2"),Arguments.of("/policy","\"unknown\""),
            Arguments.of("/job_id","\"00000000-0000-0000-0000-000000000000\""),
            Arguments.of("/attempt_id","\"00000000-0000-0000-0000-000000000000\""),
            Arguments.of("/catalog_sha256","\"wrong\""),Arguments.of("/profile_sha256","\"wrong\""),
            Arguments.of("/catalog_version","\"other-v1\""),Arguments.of("/base_model","null"),
            Arguments.of("/base_model/sha256","\"wrong\""),Arguments.of("/base_model/output_kind","\"assembled_preview\""),
            Arguments.of("/ready_for_machine","true"),Arguments.of("/quality_status","\"APPROVED\""),
            Arguments.of("/coordinate_system","\"Z-up\""),Arguments.of("/default_selection","{}"),Arguments.of("/groups","[]"),
            Arguments.of("/assets","[]"),Arguments.of("/assets/0/asset_id","\"unapproved-asset\""),
            Arguments.of("/assets/0/asset_version","\"v999\""),Arguments.of("/assets/0/source_sha256","\"wrong\""),
            Arguments.of("/assets/0/sha256","\"wrong\""),Arguments.of("/assets/0/geometry_mode","\"other\""),
            Arguments.of("/assets/0/asset_to_base_matrix_column_major","[]"),
            Arguments.of("/assets/0/asset_to_base_matrix_column_major","[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1]"),
            Arguments.of("/assets/1/asset_to_base_matrix_column_major","[1,0,0,0,0,1,0,0,0,0,1,0,0.1,0,0,1]"));
    }
    @ParameterizedTest @MethodSource("corruptions")
    void wrongAttemptTamperingUnsupportedAssetsAndInvalidTransformsAreRejected(String pointer,String json) throws Exception {
        ObjectNode tree=FitFixtures.MAPPER.valueToTree(FitFixtures.manifest(job,attempt,hash));int slash=pointer.lastIndexOf('/');
        ((ObjectNode)tree.at(pointer.substring(0,slash))).set(pointer.substring(slash+1),FitFixtures.MAPPER.readTree(json));
        var scene=FitFixtures.MAPPER.treeToValue(tree,AiCustomizationManifest.class);
        assertEquals("AI_CUSTOMIZATION_MISMATCH",assertThrows(BusinessException.class,()->check(scene)).getErrorCode());
    }
    @Test void repeatedAssetCannotSubstituteForAnotherRequiredSlot() throws Exception {
        ObjectNode tree=FitFixtures.MAPPER.valueToTree(FitFixtures.manifest(job,attempt,hash));var a=tree.withArray("assets");a.set(2,a.get(1));
        assertThrows(BusinessException.class,()->check(FitFixtures.MAPPER.treeToValue(tree,AiCustomizationManifest.class)));
    }
}
