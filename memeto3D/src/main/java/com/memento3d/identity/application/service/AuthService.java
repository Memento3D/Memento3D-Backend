package com.memento3d.identity.application.service;

import com.memento3d.identity.api.request.LoginRequest;
import com.memento3d.identity.api.request.RefreshTokenRequest;
import com.memento3d.identity.api.request.RegisterRequest;
import com.memento3d.identity.api.response.AuthResponse;
import com.memento3d.identity.api.response.LogoutResponse;
import com.memento3d.identity.api.response.RegisterResponse;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.RefreshToken;


public interface AuthService {
    RegisterResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    UserResponse getCurrentUser();
    LogoutResponse logout(String bearerToken);
    LogoutResponse logout(String bearerToken, String refreshToken);
}
