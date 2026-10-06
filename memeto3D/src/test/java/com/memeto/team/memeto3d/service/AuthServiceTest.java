package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.*;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.EmailAlreadyExistsException;
import com.memeto.team.memeto3d.exception.InvalidTokenException;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.UserRepository;
import com.memeto.team.memeto3d.security.CustomUserDetails;
import com.memeto.team.memeto3d.security.JwtService;
import com.memeto.team.memeto3d.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("hashed_password")
                .fullName("Nguyen Van A")
                .role(Role.ROLE_USER)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Register thành công - New user successfully created")
    void testRegisterSuccess() {
        RegisterRequest request = RegisterRequest.builder()
                .email("user@example.com")
                .password("123456")
                .fullName("Nguyen Van A")
                .build();

        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        RegisterResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("Register successfully", response.getMessage());
        assertEquals(sampleUser.getEmail(), response.getUser().getEmail());
        assertEquals("Nguyen Van A", response.getUser().getFullName());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Register email đã tồn tại - Throws EmailAlreadyExistsException")
    void testRegisterEmailAlreadyExists() {
        RegisterRequest request = RegisterRequest.builder()
                .email("user@example.com")
                .password("123456")
                .fullName("Nguyen Van A")
                .build();

        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Login thành công - Returns valid AuthResponse with JWT and refresh token")
    void testLoginSuccess() {
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com")
                .password("123456")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken(sampleUser)).thenReturn("sample.jwt.token");
        when(jwtService.getExpirationInSeconds()).thenReturn(3600L);
        when(refreshTokenService.createRefreshToken(sampleUser.getId())).thenReturn("sample.refresh.token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("sample.jwt.token", response.getAccessToken());
        assertEquals("sample.refresh.token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals(sampleUser.getEmail(), response.getUser().getEmail());
    }

    @Test
    @DisplayName("RefreshToken thành công - Returns new access token and rotated refresh token")
    void testRefreshTokenSuccess() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("valid.refresh.token")
                .build();

        when(refreshTokenService.validateRefreshToken("valid.refresh.token")).thenReturn(Optional.of(sampleUser.getId()));
        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken(sampleUser)).thenReturn("new.jwt.token");
        when(refreshTokenService.createRefreshToken(sampleUser.getId())).thenReturn("new.refresh.token");
        when(jwtService.getExpirationInSeconds()).thenReturn(3600L);

        AuthResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new.jwt.token", response.getAccessToken());
        assertEquals("new.refresh.token", response.getRefreshToken());
    }

    @Test
    @DisplayName("RefreshToken thất bại khi token không hợp lệ - Throws InvalidTokenException")
    void testRefreshTokenInvalid() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("invalid.token")
                .build();

        when(refreshTokenService.validateRefreshToken("invalid.token")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
    }

    @Test
    @DisplayName("Login sai password - Throws BadCredentialsException")
    void testLoginWrongPassword() {
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com")
                .password("wrongpassword")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Get current user - Returns profile when authenticated")
    void testGetCurrentUserSuccess() {
        CustomUserDetails userDetails = new CustomUserDetails(sampleUser);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUser));

        UserResponse response = authService.getCurrentUser();

        assertNotNull(response);
        assertEquals(sampleUser.getEmail(), response.getEmail());
        assertEquals(sampleUser.getFullName(), response.getFullName());
    }

    @Test
    @DisplayName("Logout - Blacklists token and clears SecurityContext")
    void testLogout() {
        String bearerToken = "Bearer sample.jwt.token";
        when(jwtService.extractExpiration("sample.jwt.token")).thenReturn(new Date(System.currentTimeMillis() + 3600000));

        LogoutResponse response = authService.logout(bearerToken);

        assertNotNull(response);
        assertEquals("Logout successfully", response.getMessage());
        verify(tokenBlacklistService, times(1)).blacklistToken(eq("sample.jwt.token"), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
