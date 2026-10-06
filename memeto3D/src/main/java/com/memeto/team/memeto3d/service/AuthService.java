package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.*;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    UserResponse getCurrentUser();
    LogoutResponse logout(String bearerToken);
}
