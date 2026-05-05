package org.example.agapibeassignment.application.service;

import org.example.agapibeassignment.rest.request.*;
import org.example.agapibeassignment.rest.response.AuthResponse;

public interface AuthService {
    void register(RegisterRequest request);
    AuthResponse verifyOtpAndCreateUser(VerifyOtpRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String authHeader);
}
