package com.memento3d.assets.application.validator;

import com.memento3d.assets.domain.exception.FileSizeExceededException;
import com.memento3d.assets.domain.exception.InvalidFileFormatException;
import com.memento3d.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Enterprise File Security and Magic Byte Validator.
 * Implements BR-SCAN-001, BR-SEC-001, and BR-SEC-002.
 */
@Component
@Slf4j
public class FileSecurityValidator {

    // Magic Bytes signatures
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47};
    private static final byte[] GIF_MAGIC = new byte[]{(byte) 0x47, (byte) 0x49, (byte) 0x46};

    private static final long MIN_IMAGE_BYTES = 1024L; // 1 KB
    private static final long MAX_IMAGE_BYTES = 25L * 1024 * 1024; // 25 MB

    private static final int MIN_DIMENSION_PX = 300;
    private static final int MAX_DIMENSION_PX = 8192;

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "heic", "webp");
    private static final Set<String> ALLOWED_IMAGE_MIMES = Set.of("image/jpeg", "image/jpg", "image/png", "image/heic", "image/webp");

    /**
     * Sanitizes filename against Path Traversal and Command Injection attacks.
     *
     * @param originalFilename raw filename from client
     * @return safe base filename
     */
    public String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unnamed_file";
        }

        // Strip path traversal sequences and null bytes
        String cleaned = originalFilename.replaceAll("[/\\\\\\r\\n\\0]", "_");
        cleaned = cleaned.replaceAll("\\.\\.+", "."); // Remove multiple dots

        // Strip non-printable / control ASCII characters
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9._\\-]", "_");

        if (cleaned.isBlank() || cleaned.equals(".")) {
            return "sanitized_file";
        }

        return cleaned;
    }

    /**
     * Full security check on uploaded image file:
     * 1. Null / empty check
     * 2. Size bounds
     * 3. MIME and extension check
     * 4. Magic bytes signature verification
     * 5. Corrupted image header / dimension bounds verification
     *
     * @param file uploaded multipart file
     */
    public void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileFormatException("Tập tin tải lên không tồn tại hoặc bị rỗng.");
        }

        long size = file.getSize();
        if (size < MIN_IMAGE_BYTES) {
            throw new InvalidFileFormatException("Dung lượng ảnh quá nhỏ (< 1KB). Tập tin có thể đã bị hỏng.");
        }

        if (size > MAX_IMAGE_BYTES) {
            throw new FileSizeExceededException("Dung lượng ảnh vượt quá giới hạn cho phép (Tối đa 25MB).");
        }

        String rawFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String extension = extractExtension(rawFilename).toLowerCase(Locale.ROOT);

        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new InvalidFileFormatException("Phần mở rộng tập tin '." + extension + "' không được hỗ trợ. Chỉ chấp nhận JPG, JPEG, PNG, HEIC, WEBP.");
        }

        String declaredMime = file.getContentType();
        if (declaredMime != null && !ALLOWED_IMAGE_MIMES.contains(declaredMime.toLowerCase(Locale.ROOT))) {
            log.warn("MIME type mismatch for file {}: declared {}", rawFilename, declaredMime);
        }

        // Verify Magic Bytes
        verifyMagicBytes(file, extension);

        // Verify Image Dimensions and Non-corrupt stream (for standard JPEG/PNG)
        if ("jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension)) {
            verifyImageDimensions(file);
        }
    }

    private void verifyMagicBytes(MultipartFile file, String extension) {
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                throw new InvalidFileFormatException("Tập tin quá ngắn hoặc không có cấu trúc header hợp lệ.");
            }

            if ("jpg".equals(extension) || "jpeg".equals(extension)) {
                if (header[0] != JPEG_MAGIC[0] || header[1] != JPEG_MAGIC[1] || header[2] != JPEG_MAGIC[2]) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "MAGIC_BYTES_MISMATCH",
                            "Nội dung file không phải định dạng JPEG hợp lệ (Magic bytes signature mismatch).");
                }
            } else if ("png".equals(extension)) {
                if (!Arrays.equals(Arrays.copyOfRange(header, 0, 4), PNG_MAGIC)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "MAGIC_BYTES_MISMATCH",
                            "Nội dung file không phải định dạng PNG hợp lệ (Magic bytes signature mismatch).");
                }
            } else if ("heic".equals(extension)) {
                // HEIF/HEIC ftyp box signature at offset 4..8
                String ftyp = new String(header, 4, 4);
                if (!"ftyp".equalsIgnoreCase(ftyp)) {
                    log.warn("HEIC missing standard ftyp box, proceeding with caution");
                }
            }
        } catch (IOException e) {
            log.error("Failed to read magic bytes for file: {}", file.getOriginalFilename(), e);
            throw new InvalidFileFormatException("Không thể đọc header tập tin.");
        }
    }

    private void verifyImageDimensions(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                throw new InvalidFileFormatException("Tập tin hình ảnh bị lỗi hoặc không thể giải mã dữ liệu điểm ảnh.");
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width < MIN_DIMENSION_PX || height < MIN_DIMENSION_PX) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IMAGE_RESOLUTION_TOO_LOW",
                        String.format("Độ phân giải ảnh quá thấp (%dx%d). Yêu cầu tối thiểu %dx%d px để tái tạo mô hình 3D sắc nét.",
                                width, height, MIN_DIMENSION_PX, MIN_DIMENSION_PX));
            }

            if (width > MAX_DIMENSION_PX || height > MAX_DIMENSION_PX) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IMAGE_RESOLUTION_TOO_HIGH",
                        String.format("Độ phân giải ảnh quá lớn (%dx%d). Giới hạn tối đa %dx%d px.",
                                width, height, MAX_DIMENSION_PX, MAX_DIMENSION_PX));
            }

        } catch (IOException e) {
            log.error("Failed to decode image dimensions: {}", file.getOriginalFilename(), e);
            throw new InvalidFileFormatException("Không thể giải mã cấu trúc ảnh.");
        }
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1) : "";
    }
}
