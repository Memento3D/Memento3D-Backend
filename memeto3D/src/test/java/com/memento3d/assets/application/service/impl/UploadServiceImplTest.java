package com.memento3d.assets.application.service.impl;

import com.memento3d.assets.domain.model.UploadChunk;
import com.memento3d.assets.domain.model.UploadChunkStatus;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.scans.domain.model.InputMode;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.domain.model.ScanStatus;

import com.memento3d.assets.api.response.UploadStatusResponse;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import com.memento3d.assets.infrastructure.persistence.repository.UploadChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UploadServiceImpl} - 100% C0 statement coverage.
 *
 * All branches covered:
 *   getUploadStatus:
 *     B1  scan not found (empty Optional)         -> ResourceNotFoundException
 *     B2  scan soft-deleted (deletedAt != null)   -> ResourceNotFoundException
 *     B3  ROLE_ADMIN bypasses IDOR                -> success
 *     B4  ROLE_OPERATOR bypasses IDOR             -> success
 *     B5  non-owner ROLE_USER                     -> ResourceNotFoundException (IDOR)
 *     B6  fileReference explicit non-blank        -> resolveFileReference returns trimmed value
 *     B7  fileReference null -> fallback Optional.of(ref)  -> success
 *     B8  fileReference blank -> fallback Optional.empty() -> 404
 *     B9  existsByScanIdAndFileReference = false  -> ResourceNotFoundException
 *     B10 all chunks uploaded                     -> completed, missingIndexes=null
 *     B11 partial chunks (pending)                -> in_progress, missingIndexes present
 *   deriveSessionStatus:
 *     D1  chunkTotal == 0                         -> in_progress
 *     D3  any chunk cancelled                     -> cancelled
 *     D4  all chunks failed                       -> failed
 *     D5  some failed, not all                    -> in_progress
 */
@ExtendWith(MockitoExtension.class)
class UploadServiceImplTest {

    @Mock private ScanRepository scanRepository;
    @Mock private UploadChunkRepository uploadChunkRepository;

    @InjectMocks private UploadServiceImpl service;

    private static final UUID SCAN_ID  = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final String FILE_REF = "video.mp4";

    private User owner;
    private Scan activeScan;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(OWNER_ID).email("owner@test.com")
                .password("pw").role(Role.ROLE_USER).build();

        activeScan = Scan.builder()
                .id(SCAN_ID).user(owner).name("Scan")
                .inputMode(InputMode.video).status(ScanStatus.uploading)
                .fileCount(0).totalSizeBytes(0L).build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UploadChunk chunk(int idx, UploadChunkStatus status) {
        return UploadChunk.builder()
                .id(UUID.randomUUID()).scan(activeScan)
                .fileReference(FILE_REF).chunkIndex(idx)
                .chunkSizeBytes(512L).status(status).build();
    }

    private void stubScanFound() {
        when(scanRepository.findById(SCAN_ID)).thenReturn(Optional.of(activeScan));
    }

    private void stubSessionExists() {
        when(uploadChunkRepository.existsByScanIdAndFileReference(SCAN_ID, FILE_REF))
                .thenReturn(true);
    }

    private void stubChunks(List<UploadChunk> chunks) {
        when(uploadChunkRepository.findByScanIdAndFileReferenceOrderByChunkIndex(SCAN_ID, FILE_REF))
                .thenReturn(chunks);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B1: scan not found
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B1: scan not found -> 404")
    void b1_scanNotFound() {
        when(scanRepository.findById(SCAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getUploadStatus(SCAN_ID, FILE_REF, OWNER_ID, Role.ROLE_USER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(SCAN_ID.toString());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B2: scan soft-deleted
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B2: scan deleted (deletedAt set) -> 404")
    void b2_scanSoftDeleted() {
        activeScan.setDeletedAt(java.time.Instant.now());
        when(scanRepository.findById(SCAN_ID)).thenReturn(Optional.of(activeScan));

        assertThatThrownBy(() ->
                service.getUploadStatus(SCAN_ID, FILE_REF, OWNER_ID, Role.ROLE_USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B3: ROLE_ADMIN bypasses IDOR
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B3: ROLE_ADMIN can access other user scan")
    void b3_adminBypassesIdor() {
        stubScanFound();
        stubSessionExists();
        stubChunks(List.of(chunk(0, UploadChunkStatus.uploaded)));

        UploadStatusResponse resp =
                service.getUploadStatus(SCAN_ID, FILE_REF, OTHER_ID, Role.ROLE_ADMIN);

        assertThat(resp.getUploadStatus()).isEqualTo("completed");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B4: ROLE_OPERATOR bypasses IDOR
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B4: ROLE_OPERATOR can access other user scan")
    void b4_operatorBypassesIdor() {
        stubScanFound();
        stubSessionExists();
        stubChunks(List.of(chunk(0, UploadChunkStatus.uploaded)));

        UploadStatusResponse resp =
                service.getUploadStatus(SCAN_ID, FILE_REF, OTHER_ID, Role.ROLE_OPERATOR);

        assertThat(resp.getUploadStatus()).isEqualTo("completed");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B5: non-owner ROLE_USER -> IDOR 404
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B5: non-owner ROLE_USER -> 404 (IDOR)")
    void b5_nonOwnerIdor() {
        stubScanFound();

        assertThatThrownBy(() ->
                service.getUploadStatus(SCAN_ID, FILE_REF, OTHER_ID, Role.ROLE_USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B6: explicit fileReference -> resolveFileReference early return, no fallback query
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B6: explicit fileReference -> no fallback query invoked")
    void b6_explicitFileRefNoFallback() {
        stubScanFound();
        stubSessionExists();
        stubChunks(List.of(chunk(0, UploadChunkStatus.pending)));

        service.getUploadStatus(SCAN_ID, FILE_REF, OWNER_ID, Role.ROLE_USER);

        verify(uploadChunkRepository, never()).findLatestFileReferenceByScanId(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B7: null fileReference -> fallback returns Optional.of(ref)
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B7: null fileReference -> fallback Optional.of -> uses that ref")
    void b7_nullFileRefFallbackPresent() {
        stubScanFound();
        when(uploadChunkRepository.findLatestFileReferenceByScanId(SCAN_ID))
                .thenReturn(Optional.of(FILE_REF));
        stubSessionExists();
        stubChunks(List.of(chunk(0, UploadChunkStatus.uploaded)));

        UploadStatusResponse resp =
                service.getUploadStatus(SCAN_ID, null, OWNER_ID, Role.ROLE_USER);

        assertThat(resp.getFileReference()).isEqualTo(FILE_REF);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B8: blank fileReference -> fallback Optional.empty -> orElse("") -> 404
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B8: blank fileReference -> fallback empty -> 404")
    void b8_blankFileRefFallbackEmpty() {
        stubScanFound();
        when(uploadChunkRepository.findLatestFileReferenceByScanId(SCAN_ID))
                .thenReturn(Optional.empty());
        when(uploadChunkRepository.existsByScanIdAndFileReference(SCAN_ID, ""))
                .thenReturn(false);

        assertThatThrownBy(() ->
                service.getUploadStatus(SCAN_ID, "   ", OWNER_ID, Role.ROLE_USER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No upload session found");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B9: session does not exist -> 404
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B9: existsByScanIdAndFileReference=false -> 404")
    void b9_sessionNotExist() {
        stubScanFound();
        when(uploadChunkRepository.existsByScanIdAndFileReference(SCAN_ID, FILE_REF))
                .thenReturn(false);

        assertThatThrownBy(() ->
                service.getUploadStatus(SCAN_ID, FILE_REF, OWNER_ID, Role.ROLE_USER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No upload session found");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B10: all chunks uploaded -> completed, missingIndexes null
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B10: all chunks uploaded -> completed, missingIndexes=null, all fields set")
    void b10_allUploaded_completed() {
        stubScanFound();
        stubSessionExists();
        stubChunks(List.of(
                chunk(0, UploadChunkStatus.uploaded),
                chunk(1, UploadChunkStatus.uploaded),
                chunk(2, UploadChunkStatus.uploaded)));

        UploadStatusResponse resp =
                service.getUploadStatus(SCAN_ID, FILE_REF, OWNER_ID, Role.ROLE_USER);

        assertThat(resp.getId()).isEqualTo(SCAN_ID);
        assertThat(resp.getFileReference()).isEqualTo(FILE_REF);
        assertThat(resp.getChunkTotal()).isEqualTo(3);
        assertThat(resp.getChunkUploaded()).isEqualTo(3);
        assertThat(resp.getUploadStatus()).isEqualTo("completed");
        assertThat(resp.getMissingIndexes()).isNull();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B11: partial upload -> in_progress, missingIndexes sorted
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("B11: partial upload -> in_progress, missingIndexes sorted ascending")
    void b11_partialUpload_inProgress() {
        stubScanFound();
        stubSessionExists();
        stubChunks(List.of(
                chunk(0, UploadChunkStatus.uploaded),
                chunk(1, UploadChunkStatus.pending),
                chunk(2, UploadChunkStatus.uploaded),
                chunk(3, UploadChunkStatus.pending)));

        UploadStatusResponse resp =
                service.getUploadStatus(SCAN_ID, FILE_REF, OWNER_ID, Role.ROLE_USER);

        assertThat(resp.getUploadStatus()).isEqualTo("in_progress");
        assertThat(resp.getChunkUploaded()).isEqualTo(2);
        assertThat(resp.getMissingIndexes()).containsExactly(1, 3);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // deriveSessionStatus private branches (invoked via getUploadStatus)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deriveSessionStatus")
    class DeriveSessionStatusTests {

        @Test
        @DisplayName("D1: zero chunks -> in_progress")
        void d1_zeroChunks() {
            stubScanFound();
            stubSessionExists();
            stubChunks(List.of());

            UploadStatusResponse resp =
                    service.getUploadStatus(SCAN_ID, FILE_REF, OWNER_ID, Role.ROLE_USER);

            assertThat(resp.getUploadStatus()).isEqualTo("in_progress");
            assertThat(resp.getChunkTotal()).isZero();
            assertThat(resp.getChunkUploaded()).isZero();
            // missingIndexes is empty list (not null) when status != completed
            assertThat(resp.getMissingIndexes()).isEmpty();
        }

        @Test
        @DisplayName("D3: any chunk cancelled -> cancelled")
        void d3_cancelled() {
            stubScanFound();
            stubSessionExists();
            stubChunks(List.of(
                    chunk(0, UploadChunkStatus.uploaded),
                    chunk(1, UploadChunkStatus.cancelled)));

            UploadStatusResponse resp =
                    service.getUploadStatus(SCAN_ID, FILE_REF, OWNER_ID, Role.ROLE_USER);

            assertThat(resp.getUploadStatus()).isEqualTo("cancelled");
        }

        @Test
        @DisplayName("D4: all chunks failed -> failed")
        void d4_allFailed() {
            stubScanFound();
            stubSessionExists();
            stubChunks(List.of(
                    chunk(0, UploadChunkStatus.failed),
                    chunk(1, UploadChunkStatus.failed)));

            UploadStatusResponse resp =
                    service.getUploadStatus(SCAN_ID, FILE_REF, OWNER_ID, Role.ROLE_USER);

            assertThat(resp.getUploadStatus()).isEqualTo("failed");
        }

        @Test
        @DisplayName("D5: some failed but not all -> in_progress")
        void d5_partialFailed() {
            stubScanFound();
            stubSessionExists();
            stubChunks(List.of(
                    chunk(0, UploadChunkStatus.uploaded),
                    chunk(1, UploadChunkStatus.failed),
                    chunk(2, UploadChunkStatus.pending)));

            UploadStatusResponse resp =
                    service.getUploadStatus(SCAN_ID, FILE_REF, OWNER_ID, Role.ROLE_USER);

            assertThat(resp.getUploadStatus()).isEqualTo("in_progress");
        }
    }
}