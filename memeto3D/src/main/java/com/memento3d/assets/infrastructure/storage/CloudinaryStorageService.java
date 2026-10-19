package com.memento3d.assets.infrastructure.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudinaryStorageService {

    private final Cloudinary cloudinary;

    public Map<String, Object> uploadImage(
            MultipartFile file,
            UUID userId,
            UUID scanId,
            UUID imageId
    ) throws IOException {

        String publicId = "memento3d/users/" + userId
                + "/scans/" + scanId
                + "/images/" + imageId;

        Map<String, Object> options = ObjectUtils.asMap(
                "public_id", publicId,
                "resource_type", "image",
                "overwrite", false
        );

        return cloudinary.uploader()
                .upload(file.getBytes(), options);
    }

    public Map<String, Object> uploadGlb(
            byte[] bytes,
            UUID userId,
            UUID scanId,
            UUID jobId,
            UUID attemptId
    ) throws IOException {

        String publicId = "memento3d/users/" + userId
                + "/scans/" + scanId
                + "/jobs/" + jobId
                + "/attempts/" + attemptId
                + "/model.glb";

        Map<String, Object> options = ObjectUtils.asMap(
                "public_id", publicId,
                "resource_type", "raw",
                "type", "upload",
                "overwrite", true
        );

        return cloudinary.uploader().upload(bytes, options);
    }

    /**
     * Uploads (or atomically replaces) the user's avatar. A fixed public id per user means the
     * previous image is overwritten, so no orphan assets remain. The image is cropped to 512x512.
     *
     * @return Cloudinary response (use {@code secure_url}, which carries a cache-busting version)
     */
    public Map<String, Object> uploadAvatar(byte[] bytes, UUID userId) throws IOException {
        Map<String, Object> options = ObjectUtils.asMap(
                "public_id", avatarPublicId(userId),
                "resource_type", "image",
                "overwrite", true,
                "invalidate", true,
                "allowed_formats", "jpg,png,webp",
                "transformation", new com.cloudinary.Transformation()
                        .width(512).height(512).crop("fill").gravity("auto").quality("auto")
        );
        return cloudinary.uploader().upload(bytes, options);
    }

    public void deleteAvatar(UUID userId) throws IOException {
        cloudinary.uploader().destroy(avatarPublicId(userId),
                ObjectUtils.asMap("resource_type", "image", "invalidate", true));
    }

    private static String avatarPublicId(UUID userId) {
        return "memento3d/users/" + userId + "/avatar";
    }
}