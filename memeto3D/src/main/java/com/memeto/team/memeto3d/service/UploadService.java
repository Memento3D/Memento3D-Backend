package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.entity.UploadSession;
import com.memeto.team.memeto3d.repository.UploadSessionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final UploadSessionRepository uploadSessionRepository;

    @Value("${app.storage.base-path:./uploads}")
    private String baseStoragePath;

    private Path tempDirPath;
    private Path finalDirPath;

    @PostConstruct
    public void init() {
        tempDirPath = Paths.get(baseStoragePath, "temp");
        finalDirPath = Paths.get(baseStoragePath, "merged");
        try {
            Files.createDirectories(tempDirPath);
            Files.createDirectories(finalDirPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directories", e);
        }
    }

    @Transactional
    public void uploadChunk(UUID sessionId, int index, MultipartFile file) {
        UploadSession session = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found"));

        if (index < 0 || index >= session.getChunkTotal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chunk index out of bounds");
        }

        if (!"pending".equals(session.getUploadStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload session is not in pending state");
        }

        try {
            Path sessionDir = tempDirPath.resolve(sessionId.toString());
            Files.createDirectories(sessionDir);

            Path chunkPath = sessionDir.resolve("chunk_" + index);
            
            // overwrite idempotent
            Files.copy(file.getInputStream(), chunkPath, StandardCopyOption.REPLACE_EXISTING);

            Set<Integer> uploadedChunks = new HashSet<>();
            if (session.getUploadedChunksBitmap() != null && !session.getUploadedChunksBitmap().isEmpty()) {
                uploadedChunks = Arrays.stream(session.getUploadedChunksBitmap().split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet());
            }

            uploadedChunks.add(index);
            session.setUploadedChunksBitmap(uploadedChunks.stream().map(String::valueOf).collect(Collectors.joining(",")));
            session.setChunkUploaded(uploadedChunks.size());

            if (session.getChunkUploaded().equals(session.getChunkTotal())) {
                boolean success = mergeChunks(session, sessionDir);
                if (success) {
                    session.setUploadStatus("completed");
                } else {
                    session.setUploadStatus("failed");
                }
            }
            
            uploadSessionRepository.save(session);

        } catch (IOException e) {
            log.error("Failed to store chunk", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store chunk");
        }
    }

    private boolean mergeChunks(UploadSession session, Path sessionDir) {
        Path mergedFilePath = finalDirPath.resolve(session.getId().toString() + "_" + session.getOriginalFilename());
        try (OutputStream os = Files.newOutputStream(mergedFilePath)) {
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            
            for (int i = 0; i < session.getChunkTotal(); i++) {
                Path chunkPath = sessionDir.resolve("chunk_" + i);
                if (!Files.exists(chunkPath)) {
                    log.error("Missing chunk " + i + " during merge");
                    return false;
                }
                
                try (InputStream is = Files.newInputStream(chunkPath)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                        md5Digest.update(buffer, 0, bytesRead);
                    }
                }
            }
            
            String calculatedMd5 = bytesToHex(md5Digest.digest());
            if (session.getExpectedChecksum() != null && !session.getExpectedChecksum().isEmpty()) {
                if (!calculatedMd5.equalsIgnoreCase(session.getExpectedChecksum())) {
                    log.error("Checksum mismatch for session " + session.getId());
                    return false;
                }
            }
            
            // Clean up chunks if successful
            for (int i = 0; i < session.getChunkTotal(); i++) {
                Files.deleteIfExists(sessionDir.resolve("chunk_" + i));
            }
            Files.deleteIfExists(sessionDir);
            
            return true;

        } catch (Exception e) {
            log.error("Error merging chunks for session " + session.getId(), e);
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
