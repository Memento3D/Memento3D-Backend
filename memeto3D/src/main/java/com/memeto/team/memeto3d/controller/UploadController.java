package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chunked Uploads", description = "Endpoints for handling large file chunked uploads")
public class UploadController {

    private final UploadService uploadService;

    @PostMapping(value = "/{id}/chunks/{index}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a file chunk",
            description = "Uploads a specific chunk of a file. Supports retry on duplicate chunks. Merges chunks when the last one is received."
    )
    public ResponseEntity<?> uploadChunk(
            @PathVariable("id") UUID id,
            @PathVariable("index") int index,
            @RequestParam("file") MultipartFile file
    ) {
        log.info("Received chunk {} for upload session {}", index, id);
        uploadService.uploadChunk(id, index, file);
        return ResponseEntity.ok().build();
    }
}
