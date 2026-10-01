package com.memeto.team.memeto3d;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memeto.team.memeto3d.dto.LoginRequest;
import com.memeto.team.memeto3d.dto.RegisterRequest;
import com.memeto.team.memeto3d.dto.AuthResponse;
import com.memeto.team.memeto3d.entity.*;
import com.memeto.team.memeto3d.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/v1/uploads/{id}/status.
 *
 * Acceptance criteria covered:
 *   AC-1  Trả đúng danh sách chunk còn thiếu sau khi bị ngắt giữa chừng.
 *   AC-2  Session completed → trả trạng thái rõ ràng, không còn missing_indexes.
 *   AC-3  Session không tồn tại → 404.
 *   AC-4  IDOR: non-owner → 404.
 *   AC-5  Unauthorized (no JWT) → 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UploadStatusIntegrationTest {

    // ─── constants ───────────────────────────────────────────────────────────
    private static final String ENDPOINT = "/api/v1/uploads/{id}/status";
    private static final String FILE_REF  = "video_session_001.mp4";
    private static final int    TOTAL     = 10;

    // ─── spring beans ────────────────────────────────────────────────────────
    @Autowired private MockMvc               mockMvc;
    @Autowired private ObjectMapper          objectMapper;
    @Autowired private UserRepository        userRepository;
    @Autowired private ScanRepository        scanRepository;
    @Autowired private UploadChunkRepository uploadChunkRepository;
    @Autowired private BlacklistedTokenRepository blacklistedTokenRepository;

    // ─── shared state (set up once per test class) ───────────────────────────
    private static String ownerToken;
    private static String otherToken;
    private static UUID   scanId;

    // ─── database cleanup ────────────────────────────────────────────────────
    @BeforeEach
    void cleanDatabase() {
        uploadChunkRepository.deleteAll();
        scanRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** Register + login and return the JWT. */
    private String registerAndLogin(String email) throws Exception {
        // Register
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                        .email(email)
                        .password("Password123!")
                        .fullName("Test User")
                        .build())))
                .andExpect(status().isCreated());

        // Login
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        LoginRequest.builder().email(email).password("Password123!").build())))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();
    }

    /**
     * Seeds an upload session with {@code TOTAL} chunks.
     * Chunks in {@code uploadedIndexes} get status=uploaded, rest stay pending.
     */
    private UUID seedScanWithChunks(User owner, int[] uploadedIndexes) {
        Scan scan = scanRepository.saveAndFlush(Scan.builder()
                .user(owner)
                .name("Test Scan")
                .inputMode(InputMode.video)
                .status(ScanStatus.uploading)
                .fileCount(0)
                .totalSizeBytes(0L)
                .build());

        java.util.Set<Integer> uploaded = new java.util.HashSet<>();
        for (int i : uploadedIndexes) {
            uploaded.add(i);
        }

        for (int i = 0; i < TOTAL; i++) {
            UploadChunkStatus status = uploaded.contains(i)
                    ? UploadChunkStatus.uploaded
                    : UploadChunkStatus.pending;
            Instant uploadedAt = UploadChunkStatus.uploaded.equals(status)
                    ? Instant.now() : null;

            uploadChunkRepository.save(UploadChunk.builder()
                    .scan(scan)
                    .fileReference(FILE_REF)
                    .chunkIndex(i)
                    .chunkSizeBytes(1024L * 512)   // 512 KB each
                    .storagePath(UploadChunkStatus.uploaded.equals(status)
                            ? "/storage/chunks/" + FILE_REF + "/chunk_" + i : null)
                    .status(status)
                    .uploadedAt(uploadedAt)
                    .build());
        }
        return scan.getId();
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    /**
     * AC-5: No JWT → 401.
     */
    @Test
    @DisplayName("AC-5: No JWT → 401 Unauthorized")
    void test_noJwt_returns401() throws Exception {
        mockMvc.perform(get(ENDPOINT, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * AC-3: Scan/session does not exist → 404.
     */
    @Test
    @DisplayName("AC-3: Session không tồn tại → 404")
    void test_sessionNotFound_returns404() throws Exception {
        String token = registerAndLogin("owner404@test.com");

        mockMvc.perform(get(ENDPOINT, UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /**
     * AC-1: After interruption (some chunks uploaded, some pending),
     *        missing_indexes must list exactly the missing ones.
     */
    @Test
    @DisplayName("AC-1: Trả đúng missing_indexes sau khi bị ngắt giữa chừng")
    void test_partialUpload_returnsMissingIndexes() throws Exception {
        String token = registerAndLogin("partial@test.com");
        User owner   = userRepository.findByEmail("partial@test.com").orElseThrow();

        // Chunks 0-6 uploaded (7 chunks); chunks 7, 8, 9 still pending
        int[] uploadedIndexes = {0, 1, 2, 3, 4, 5, 6};
        UUID sid = seedScanWithChunks(owner, uploadedIndexes);

        mockMvc.perform(get(ENDPOINT, sid)
                .param("file_reference", FILE_REF)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sid.toString()))
                .andExpect(jsonPath("$.file_reference").value(FILE_REF))
                .andExpect(jsonPath("$.chunk_total").value(TOTAL))
                .andExpect(jsonPath("$.chunk_uploaded").value(7))
                .andExpect(jsonPath("$.upload_status").value("in_progress"))
                // missing_indexes must be [7, 8, 9] — sorted ascending
                .andExpect(jsonPath("$.missing_indexes", hasSize(3)))
                .andExpect(jsonPath("$.missing_indexes[0]").value(7))
                .andExpect(jsonPath("$.missing_indexes[1]").value(8))
                .andExpect(jsonPath("$.missing_indexes[2]").value(9));
    }

    /**
     * AC-1 (variant): Specifically interrupted in the middle (non-consecutive gaps).
     */
    @Test
    @DisplayName("AC-1 (variant): Non-consecutive missing chunks returned in sorted order")
    void test_nonConsecutiveMissing_returnsSortedIndexes() throws Exception {
        String token = registerAndLogin("gaps@test.com");
        User owner   = userRepository.findByEmail("gaps@test.com").orElseThrow();

        // Uploaded: 0,1,2,3,4,6,8 (7 chunks) — missing: 5, 7, 9
        int[] uploadedIndexes = {0, 1, 2, 3, 4, 6, 8};
        UUID sid = seedScanWithChunks(owner, uploadedIndexes);

        mockMvc.perform(get(ENDPOINT, sid)
                .param("file_reference", FILE_REF)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunk_total").value(TOTAL))
                .andExpect(jsonPath("$.chunk_uploaded").value(7))
                .andExpect(jsonPath("$.upload_status").value("in_progress"))
                .andExpect(jsonPath("$.missing_indexes", hasSize(3)))
                .andExpect(jsonPath("$.missing_indexes[0]").value(5))
                .andExpect(jsonPath("$.missing_indexes[1]").value(7))
                .andExpect(jsonPath("$.missing_indexes[2]").value(9));
    }

    /**
     * AC-2: All chunks uploaded → upload_status=completed, no missing_indexes.
     */
    @Test
    @DisplayName("AC-2: Session completed → upload_status=completed, missing_indexes absent")
    void test_allChunksUploaded_statusCompleted() throws Exception {
        String token = registerAndLogin("completed@test.com");
        User owner   = userRepository.findByEmail("completed@test.com").orElseThrow();

        int[] allIndexes = new int[TOTAL];
        for (int i = 0; i < TOTAL; i++) allIndexes[i] = i;
        UUID sid = seedScanWithChunks(owner, allIndexes);

        mockMvc.perform(get(ENDPOINT, sid)
                .param("file_reference", FILE_REF)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunk_total").value(TOTAL))
                .andExpect(jsonPath("$.chunk_uploaded").value(TOTAL))
                .andExpect(jsonPath("$.upload_status").value("completed"))
                // missing_indexes must NOT be present in the response
                .andExpect(jsonPath("$.missing_indexes").doesNotExist());
    }

    /**
     * AC-4: IDOR — another user trying to access the session → 404.
     */
    @Test
    @DisplayName("AC-4: IDOR — non-owner → 404")
    void test_idor_nonOwner_returns404() throws Exception {
        String ownerToken = registerAndLogin("idor_owner@test.com");
        String otherToken = registerAndLogin("idor_other@test.com");

        User owner = userRepository.findByEmail("idor_owner@test.com").orElseThrow();
        UUID sid   = seedScanWithChunks(owner, new int[]{0, 1, 2});

        // Other user must receive 404, not 200 or 403
        mockMvc.perform(get(ENDPOINT, sid)
                .param("file_reference", FILE_REF)
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // Owner can still access
        mockMvc.perform(get(ENDPOINT, sid)
                .param("file_reference", FILE_REF)
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }
}
