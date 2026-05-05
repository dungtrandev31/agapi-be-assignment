package org.example.agapibeassignment.rest.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank(message = "Identifier is required")
    private String identifier;

    @NotBlank(message = "OTP code is required")
    private String otpCode;
}
