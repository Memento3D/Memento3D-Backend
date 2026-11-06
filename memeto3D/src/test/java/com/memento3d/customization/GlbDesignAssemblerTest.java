package com.memento3d.customization;
import com.fasterxml.jackson.databind.*;
import com.memento3d.customization.application.service.GlbDesignAssembler;
import org.junit.jupiter.api.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GlbDesignAssemblerTest {
    final ObjectMapper mapper=new ObjectMapper();final GlbDesignAssembler assembler=new GlbDesignAssembler(mapper);
    byte[] triangle() throws Exception {
        var doc=mapper.readTree("""
            {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
             "buffers":[{"byteLength":36}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36}],
             "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
             "meshes":[{"primitives":[{"attributes":{"POSITION":0},"material":0}]}],
             "materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],"textures":[{"source":0}],
             "images":[{"bufferView":0,"mimeType":"image/png"}]}
            """);
        byte[] json=mapper.writeValueAsBytes(doc);int padded=(json.length+3)/4*4;
        ByteBuffer file=ByteBuffer.allocate(28+padded+36).order(ByteOrder.LITTLE_ENDIAN);
        file.putInt(0x46546c67).putInt(2).putInt(file.capacity()).putInt(padded).putInt(0x4e4f534a).put(json);
        while(file.position()<20+padded)file.put((byte)' ');file.putInt(36).putInt(0x004e4942);
        for(float value:new float[]{0,0,0,1,0,0,0,1,0})file.putFloat(value);return file.array();
    }
    JsonNode json(byte[] glb)throws Exception{var file=ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);int size=file.getInt(12);return mapper.readTree(Arrays.copyOfRange(glb,20,20+size));}
    byte[] bin(byte[] glb){var file=ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);return Arrays.copyOfRange(glb,28+file.getInt(12),glb.length);}
    @Test void combinesWithoutChangingGeometryAndRebasesTexturesAndPlacement()throws Exception {
        byte[] source=triangle();var matrix=List.of(1d,0d,0d,0d,0d,1d,0d,0d,0d,0d,1d,0d,2d,0d,0d,1d);
        byte[] output=assembler.assemble(List.of(new GlbDesignAssembler.Part(source,null),new GlbDesignAssembler.Part(source,matrix)),12);
        JsonNode doc=json(output);assertEquals(2,doc.path("meshes").size());assertEquals(2,doc.path("materials").size());
        assertEquals(1,doc.path("materials").get(1).path("pbrMetallicRoughness").path("baseColorTexture").path("index").intValue());
        assertEquals(1,doc.path("images").get(1).path("bufferView").intValue());
        assertEquals(36,doc.path("bufferViews").get(1).path("byteOffset").intValue());
        assertEquals(matrix,mapper.convertValue(doc.path("nodes").get(3).path("matrix"),new com.fasterxml.jackson.core.type.TypeReference<List<Double>>(){}));
        assertEquals(0.12,doc.path("nodes").get(4).path("scale").get(0).asDouble(),1e-10);
        assertArrayEquals(bin(source),Arrays.copyOfRange(bin(output),0,36));assertArrayEquals(bin(source),Arrays.copyOfRange(bin(output),36,72));
    }
    @Test void rejectsInvalidHeader() {assertThrows(Exception.class,()->assembler.assemble(List.of(new GlbDesignAssembler.Part(new byte[20],null)),12));}
    @Test void assemblesActualMvpAssetsWhenLocalFixtureIsProvided()throws Exception {
        String root=System.getProperty("custom.fixture.root");Assumptions.assumeTrue(root!=null,"Optional local GLB fixture");
        Path ai=Path.of(root);Path validation=ai.resolve("validation/customization/rdf-objective-acceptance-v1-final");
        Path base=validation.resolve("jobs/b6dc1371-9615-4158-b56f-e9187835ac75/render/scene.glb");
        Path hair=validation.resolve("accessories-final-04/hair-side-part.glb"),glasses=validation.resolve("accessories-final-04/glasses-round.glb");
        byte[] source=Files.readAllBytes(base);
        byte[] combined=assembler.assemble(List.of(new GlbDesignAssembler.Part(source,null),new GlbDesignAssembler.Part(Files.readAllBytes(hair),null),new GlbDesignAssembler.Part(Files.readAllBytes(glasses),null)),12);
        JsonNode before=json(source),after=json(combined);assertTrue(after.path("meshes").size()>before.path("meshes").size());
        assertArrayEquals(bin(source),Arrays.copyOfRange(bin(combined),0,bin(source).length));
        Path output=Path.of("target/custom-print-validation/hair-glasses-12cm.glb");Files.createDirectories(output.getParent());Files.write(output,combined);
        JsonNode manifest=mapper.readTree(validation.resolve("accessories-final-04/manifest.json").toFile());
        JsonNode capData=manifest.path("assets").get(0);
        List<Double> placement=mapper.convertValue(capData.path("asset_to_base_matrix_column_major"),new com.fasterxml.jackson.core.type.TypeReference<>(){});
        Path cap=ai.resolve("assets/custom-library/mvp-selected-preview-v1/assets/graduation-cap/v1/preview.glb");
        byte[] capped=assembler.assemble(List.of(new GlbDesignAssembler.Part(source,null),new GlbDesignAssembler.Part(Files.readAllBytes(cap),placement),new GlbDesignAssembler.Part(Files.readAllBytes(glasses),null)),12);
        assertTrue(json(capped).path("meshes").size()>before.path("meshes").size());Files.write(output.resolveSibling("cap-glasses-12cm.glb"),capped);
    }
}
