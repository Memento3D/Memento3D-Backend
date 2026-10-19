package com.memento3d.identity.application.service;

import com.memento3d.identity.api.request.ConsentRequest;
import com.memento3d.identity.api.request.LoginRequest;
import com.memento3d.identity.api.request.RefreshTokenRequest;
import com.memento3d.identity.api.request.RegisterRequest;
import com.memento3d.identity.api.request.ResendVerificationRequest;
import com.memento3d.identity.api.request.VerifyEmailRequest;
import com.memento3d.identity.api.response.AuthResponse;
import com.memento3d.identity.api.response.LegalDocumentResponse;
import com.memento3d.identity.api.response.LogoutResponse;
import com.memento3d.identity.api.response.RegisterResponse;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.api.response.VerifyEmailResponse;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    UserResponse getCurrentUser();
    LogoutResponse logout(String bearerToken);
    LogoutResponse logout(String bearerToken, String refreshToken);

    VerifyEmailResponse verifyEmail(VerifyEmailRequest request);
    void resendVerificationEmail(ResendVerificationRequest request);
    AuthResponse completeConsents(ConsentRequest request, String ipAddress, String userAgent);
    LegalDocumentResponse getCurrentLegalDocument(String type);
}
