package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.*;

public interface AuthService {

    /**
     * Registers a new user in the system.
     *
     * @param request the registration details
     * @return the registration response containing created user info
     */
    RegisterResponse register(RegisterRequest request);

    /**
     * Authenticates user credentials and generates a JWT access token.
     *
     * @param request the login credentials (email & password)
     * @return the authentication response with access token and user info
     */
    AuthResponse login(LoginRequest request);

    /**
     * Retrieves the currently authenticated user from SecurityContext.
     *
     * @return the user details
     */
    UserResponse getCurrentUser();

    /**
     * Logs out the user by revoking the current JWT token and adding it to the blacklist.
     *
     * @param bearerToken the Authorization header value (Bearer <token>)
     * @return logout confirmation
     */
    LogoutResponse logout(String bearerToken);
}
