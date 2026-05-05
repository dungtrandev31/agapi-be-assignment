package org.example.agapibeassignment.application.service;

public interface OtpService {
    String generateAndSend(String identifier, String purpose);
    boolean verify(String identifier, String purpose, String otpCode);
    boolean isVerified(String identifier, String purpose);
    void clearVerified(String identifier, String purpose);
}
