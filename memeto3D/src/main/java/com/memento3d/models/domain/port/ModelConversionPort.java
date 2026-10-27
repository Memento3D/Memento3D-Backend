package com.memento3d.models.domain.port;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port interface for converting 3D visualization models (GLB) to manufacturing geometry (STL).
 * Isolates mesh preparation from web request lifecycles.
 */
public interface ModelConversionPort {

    ConversionResult convertGlbToStl(UUID modelId, byte[] glbBytes, String targetUnit, ModelDimensions dimensions);

    record ModelDimensions(
            BigDecimal widthMm,
            BigDecimal heightMm,
            BigDecimal depthMm,
            Double scale
    ) {
        public static ModelDimensions defaultDimensions() {
            return new ModelDimensions(
                    BigDecimal.valueOf(80.0),
                    BigDecimal.valueOf(100.0),
                    BigDecimal.valueOf(80.0),
                    1.0
            );
        }
    }

    record ConversionResult(
            boolean success,
            byte[] stlBytes,
            String format,
            String sha256,
            long fileSizeBytes,
            String statusMessage
    ) {
        public static ConversionResult success(byte[] stlBytes, String sha256) {
            return new ConversionResult(
                    true,
                    stlBytes,
                    "STL",
                    sha256,
                    stlBytes != null ? stlBytes.length : 0L,
                    "Chuyển đổi hình học STL sẵn sàng cho CHITUBOX."
            );
        }

        public static ConversionResult pending(String message) {
            return new ConversionResult(false, null, "STL", null, 0L, message);
        }
    }
}
