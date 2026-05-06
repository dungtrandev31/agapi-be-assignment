package org.com.application.service;

import org.com.rest.request.LoginRequest;
import org.com.rest.request.RefreshTokenRequest;
import org.com.rest.request.RegisterRequest;
import org.com.rest.request.VerifyOtpRequest;
import org.example.agapibeassignment.rest.request.*;
import org.com.rest.response.AuthResponse;

public interface AuthService {
    void register(RegisterRequest request);
    AuthResponse verifyOtpAndCreateUser(VerifyOtpRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String authHeader);
}
