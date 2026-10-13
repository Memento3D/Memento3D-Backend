package com.memento3d.assets.api;

import com.memento3d.assets.api.response.UploadStatusResponse;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.assets.application.service.UploadChunkService;
import com.memento3d.assets.application.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UploadController} - 100% C0 coverage.
 *
 * Branches in getAuthenticatedUserDetails():
 *   C1  userDetails != null && id != null         -> returns userDetails directly
 *   C2  userDetails == null, auth == null          -> InvalidTokenException
 *   C3  userDetails == null, anonymous user        -> InvalidTokenException
 *   C4  userDetails == null, not authenticated     -> InvalidTokenException
 *   C5  userDetails == null, principal instanceof CustomUserDetails -> returns it
 *   C6  userDetails == null, principal is other type -> InvalidTokenException
 */
@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    @Mock private UploadChunkService uploadChunkService;
    @Mock private UploadService uploadService;
    @InjectMocks private UploadController controller;

    private static final UUID SCAN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private CustomUserDetails validUserDetails;
    private UploadStatusResponse sampleResponse;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(USER_ID).email("u@test.com")
                .password("pw").role(Role.ROLE_USER).build();
        validUserDetails = new CustomUserDetails(user);

        sampleResponse = UploadStatusResponse.builder()
                .id(SCAN_ID).fileReference("f.mp4")
                .chunkTotal(5).chunkUploaded(3)
                .uploadStatus("in_progress")
                .missingIndexes(java.util.List.of(1, 3, 4))
                .build();
    }

    // ─── C1: userDetails injected directly ───────────────────────────────────
    @Test
    @DisplayName("C1: valid userDetails injected -> delegates to service, returns 200")
    void c1_validUserDetails() {
        when(uploadService.getUploadStatus(SCAN_ID, "f.mp4", USER_ID, Role.ROLE_USER))
                .thenReturn(sampleResponse);

        ResponseEntity<UploadStatusResponse> resp =
                controller.getUploadStatus(SCAN_ID, "f.mp4", validUserDetails);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isSameAs(sampleResponse);
        verify(uploadService).getUploadStatus(SCAN_ID, "f.mp4", USER_ID, Role.ROLE_USER);
    }

    // ─── C2: userDetails == null AND auth == null ─────────────────────────────
    @Test
    @DisplayName("C2: null userDetails, null auth -> InvalidTokenException")
    void c2_nullUserDetails_nullAuth() {
        try (MockedStatic<SecurityContextHolder> holder =
                     mockStatic(SecurityContextHolder.class)) {
            SecurityContext ctx = mock(SecurityContext.class);
            holder.when(SecurityContextHolder::getContext).thenReturn(ctx);
            when(ctx.getAuthentication()).thenReturn(null);

            assertThatThrownBy(() ->
                    controller.getUploadStatus(SCAN_ID, "f.mp4", null))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("No authenticated user");
        }
    }

    // ─── C3: userDetails == null AND principal == "anonymousUser" ────────────
    @Test
    @DisplayName("C3: null userDetails, anonymous principal -> InvalidTokenException")
    void c3_nullUserDetails_anonymous() {
        try (MockedStatic<SecurityContextHolder> holder =
                     mockStatic(SecurityContextHolder.class)) {
            SecurityContext ctx = mock(SecurityContext.class);
            Authentication auth = mock(Authentication.class);
            holder.when(SecurityContextHolder::getContext).thenReturn(ctx);
            when(ctx.getAuthentication()).thenReturn(auth);
            when(auth.isAuthenticated()).thenReturn(true);
            when(auth.getPrincipal()).thenReturn("anonymousUser");

            assertThatThrownBy(() ->
                    controller.getUploadStatus(SCAN_ID, "f.mp4", null))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    // ─── C4: userDetails == null AND not authenticated ───────────────────────
    @Test
    @DisplayName("C4: null userDetails, not authenticated -> InvalidTokenException")
    void c4_nullUserDetails_notAuthenticated() {
        try (MockedStatic<SecurityContextHolder> holder =
                     mockStatic(SecurityContextHolder.class)) {
            SecurityContext ctx = mock(SecurityContext.class);
            Authentication auth = mock(Authentication.class);
            holder.when(SecurityContextHolder::getContext).thenReturn(ctx);
            when(ctx.getAuthentication()).thenReturn(auth);
            when(auth.isAuthenticated()).thenReturn(false);

            assertThatThrownBy(() ->
                    controller.getUploadStatus(SCAN_ID, "f.mp4", null))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    // ─── C5: userDetails == null AND principal instanceof CustomUserDetails ───
    @Test
    @DisplayName("C5: null userDetails, principal is CustomUserDetails -> success")
    void c5_nullUserDetails_principalIsCustomUserDetails() {
        when(uploadService.getUploadStatus(eq(SCAN_ID), eq("f.mp4"), eq(USER_ID), eq(Role.ROLE_USER)))
                .thenReturn(sampleResponse);

        try (MockedStatic<SecurityContextHolder> holder =
                     mockStatic(SecurityContextHolder.class)) {
            SecurityContext ctx = mock(SecurityContext.class);
            Authentication auth = mock(Authentication.class);
            holder.when(SecurityContextHolder::getContext).thenReturn(ctx);
            when(ctx.getAuthentication()).thenReturn(auth);
            when(auth.isAuthenticated()).thenReturn(true);
            // principal is NOT "anonymousUser" string — use non-anonymous string first
            // then return our CustomUserDetails (instanceof check)
            when(auth.getPrincipal()).thenReturn(validUserDetails);

            ResponseEntity<UploadStatusResponse> resp =
                    controller.getUploadStatus(SCAN_ID, "f.mp4", null);

            assertThat(resp.getStatusCode().value()).isEqualTo(200);
        }
    }

    // ─── C6: userDetails == null AND principal is other type ─────────────────
    @Test
    @DisplayName("C6: null userDetails, principal is unknown type -> InvalidTokenException")
    void c6_nullUserDetails_principalIsUnknownType() {
        try (MockedStatic<SecurityContextHolder> holder =
                     mockStatic(SecurityContextHolder.class)) {
            SecurityContext ctx = mock(SecurityContext.class);
            Authentication auth = mock(Authentication.class);
            holder.when(SecurityContextHolder::getContext).thenReturn(ctx);
            when(ctx.getAuthentication()).thenReturn(auth);
            when(auth.isAuthenticated()).thenReturn(true);
            // principal is something that is NOT "anonymousUser" AND NOT CustomUserDetails
            when(auth.getPrincipal()).thenReturn(new Object());

            assertThatThrownBy(() ->
                    controller.getUploadStatus(SCAN_ID, "f.mp4", null))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("Invalid user details");
        }
    }

    // ─── userDetails != null but id == null (edge) ───────────────────────────
    @Test
    @DisplayName("Edge: userDetails.id == null -> falls through to SecurityContextHolder")
    void edge_userDetailsNullId() {
        // Create a spy with null ID to test the id==null branch
        User userWithNullId = User.builder()
                .id(null).email("x@test.com")
                .password("pw").role(Role.ROLE_USER).build();
        CustomUserDetails detailsNullId = new CustomUserDetails(userWithNullId);

        try (MockedStatic<SecurityContextHolder> holder =
                     mockStatic(SecurityContextHolder.class)) {
            SecurityContext ctx = mock(SecurityContext.class);
            holder.when(SecurityContextHolder::getContext).thenReturn(ctx);
            when(ctx.getAuthentication()).thenReturn(null);

            assertThatThrownBy(() ->
                    controller.getUploadStatus(SCAN_ID, "f.mp4", detailsNullId))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }
}