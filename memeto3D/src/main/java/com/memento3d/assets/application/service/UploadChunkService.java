package com.memento3d.assets.application.service;

import com.memento3d.assets.domain.model.UploadChunk;

import com.memento3d.assets.domain.model.UploadSession;
import com.memento3d.assets.infrastructure.persistence.repository.UploadSessionRepository;
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

/**
 * Handles the actual chunk file I/O: receives individual chunks, stores them
 * to temp storage, and merges them into the final file once all chunks arrive.
 *
 * <p>Renamed from {@code UploadService} (origin/main) to {@code UploadChunkService}
 * to avoid a name collision with the {@link UploadService} interface that exposes
 * the status-query API.</p>
 */
@Service
@Slf4j
public class UploadChunkService {

    private final UploadSessionRepository uploadSessionRepository;

    public UploadChunkService(UploadSessionRepository uploadSessionRepository) {
        this.uploadSessionRepository = uploadSessionRepository;
    }

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
            session.setUploadedChunksBitmap(
                    uploadedChunks.stream().map(String::valueOf).collect(Collectors.joining(",")));
            session.setChunkUploaded(uploadedChunks.size());

            if (session.getChunkUploaded().equals(session.getChunkTotal())) {
                boolean success = mergeChunks(session, sessionDir);
                session.setUploadStatus(success ? "completed" : "failed");
            }

            uploadSessionRepository.save(session);

        } catch (IOException e) {
            log.error("Failed to store chunk", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store chunk");
        }
    }

    private boolean mergeChunks(UploadSession session, Path sessionDir) {
        Path mergedFilePath = finalDirPath.resolve(
                session.getId().toString() + "_" + session.getOriginalFilename());
        try (OutputStream os = Files.newOutputStream(mergedFilePath)) {
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");

            for (int i = 0; i < session.getChunkTotal(); i++) {
                Path chunkPath = sessionDir.resolve("chunk_" + i);
                if (!Files.exists(chunkPath)) {
                    log.error("Missing chunk {} during merge", i);
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
                    log.error("Checksum mismatch for session {}", session.getId());
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
            log.error("Error merging chunks for session {}", session.getId(), e);
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

    public void abortUploadSession(UUID sessionId) {
        if (tempDirPath != null) {
            Path sessionDir = tempDirPath.resolve(sessionId.toString());
            if (Files.exists(sessionDir)) {
                try (var stream = Files.walk(sessionDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {}
                            });
                } catch (IOException e) {
                    log.warn("Failed to clean up upload session dir {}", sessionId, e);
                }
            }
        }
        uploadSessionRepository.deleteById(sessionId);
    }
}