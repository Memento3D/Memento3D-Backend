package com.memento3d.models.infrastructure.conversion;

import com.memento3d.models.domain.port.ModelConversionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component("blenderModelConversionAdapter")
@Slf4j
public class BlenderModelConversionAdapter implements ModelConversionPort {

    private final String blenderPath;
    private final boolean conversionEnabled;

    public BlenderModelConversionAdapter(
            @Value("${app.production.blender-path:blender}") String blenderPath,
            @Value("${app.production.conversion-enabled:false}") boolean conversionEnabled
    ) {
        this.blenderPath = blenderPath;
        this.conversionEnabled = conversionEnabled;
    }

    @Override
    public ConversionResult convertGlbToStl(UUID modelId, byte[] glbBytes, String targetUnit, ModelDimensions dimensions) {
        if (!conversionEnabled) {
            log.info("Automated Blender conversion is disabled via config. Marking model {} as pending manual preparation.", modelId);
            return ConversionResult.pending("Chờ nhân viên xưởng xuất STL từ GLB bằng Blender/Meshmixer theo kích thước yêu cầu.");
        }

        if (glbBytes == null || glbBytes.length < 12) {
            return ConversionResult.pending("File GLB gốc không hợp lệ hoặc thiếu dữ liệu nhị phân.");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("memento3d-conv-" + modelId);
            Path inputGlb = tempDir.resolve("input.glb");
            Path outputStl = tempDir.resolve("output.stl");
            Path scriptPy = tempDir.resolve("convert.py");

            Files.write(inputGlb, glbBytes);

            String pythonScript = """
                    import bpy
                    import sys
                    argv = sys.argv[sys.argv.index("--") + 1:]
                    in_path, out_path = argv[0], argv[1]
                    bpy.ops.wm.read_factory_settings(use_empty=True)
                    bpy.ops.import_scene.gltf(filepath=in_path)
                    bpy.ops.wm.stl_export(filepath=out_path)
                    """;
            Files.writeString(scriptPy, pythonScript);

            ProcessBuilder pb = new ProcessBuilder(
                    blenderPath,
                    "-b",
                    "--python", scriptPy.toString(),
                    "--", inputGlb.toString(), outputStl.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(45, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.warn("Blender conversion timed out after 45s for model {}", modelId);
                return ConversionResult.pending("Quá trình xuất STL vượt quá giới hạn thời gian (45s). Chờ kiểm tra kỹ thuật.");
            }

            if (process.exitValue() == 0 && Files.exists(outputStl) && Files.size(outputStl) > 80) {
                byte[] stlBytes = Files.readAllBytes(outputStl);
                String sha256 = sha256(stlBytes);
                log.info("Blender conversion successful for model {}: {} bytes, SHA-256: {}", modelId, stlBytes.length, sha256);
                return ConversionResult.success(stlBytes, sha256);
            } else {
                log.warn("Blender process exited with code {} for model {}", process.exitValue(), modelId);
                return ConversionResult.pending("Blender CLI không hoàn tất chuyển đổi. Chờ nhân viên xuất STL thủ công.");
            }

        } catch (IOException ex) {
            log.info("Blender CLI not found in runtime environment ({}). Graceful fallback to manual preparation.", ex.getMessage());
            return ConversionResult.pending("Hệ thống chưa nạp Blender CLI. Kỹ thuật viên sẽ tải GLB và xuất STL trong CHITUBOX.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ConversionResult.pending("Quá trình chuyển đổi bị gián đoạn.");
        } finally {
            if (tempDir != null) {
                deleteDir(tempDir.toFile());
            }
        }
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (NoSuchAlgorithmException ex) {
            return "";
        }
    }
}
