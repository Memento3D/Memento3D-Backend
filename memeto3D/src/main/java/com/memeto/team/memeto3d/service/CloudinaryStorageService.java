package com.memeto.team.memeto3d.service;

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
}