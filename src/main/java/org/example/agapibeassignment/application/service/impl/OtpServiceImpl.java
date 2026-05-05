package org.example.agapibeassignment.application.service.impl;

import org.example.agapibeassignment.application.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class OtpServiceImpl implements OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);
    private static final String OTP_KEY = "otp:";
    private static final String VERIFIED_KEY = "otp_verified:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final int otpLength;
    private final int otpExpiryMinutes;
    private final SecureRandom random = new SecureRandom();

    public OtpServiceImpl(RedisTemplate<String, Object> redisTemplate,
                          @Value("${app.otp.length}") int otpLength,
                          @Value("${app.otp.expiry-minutes}") int otpExpiryMinutes) {
        this.redisTemplate = redisTemplate;
        this.otpLength = otpLength;
        this.otpExpiryMinutes = otpExpiryMinutes;
    }

    @Override
    public String generateAndSend(String identifier, String purpose) {
        String otp = String.format("%0" + otpLength + "d", random.nextInt((int) Math.pow(10, otpLength)));
        redisTemplate.opsForValue().set(OTP_KEY + purpose + ":" + identifier, otp, otpExpiryMinutes, TimeUnit.MINUTES);
        log.info("======== [MOCK OTP] To: {} Code: {} Purpose: {} (expires {}min) ========", identifier, otp, purpose, otpExpiryMinutes);
        return otp;
    }

    @Override
    public boolean verify(String identifier, String purpose, String otpCode) {
        String key = OTP_KEY + purpose + ":" + identifier;
        Object stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.toString().equals(otpCode)) return false;
        redisTemplate.delete(key);
        redisTemplate.opsForValue().set(VERIFIED_KEY + purpose + ":" + identifier, "true", 10, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public boolean isVerified(String identifier, String purpose) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(VERIFIED_KEY + purpose + ":" + identifier));
    }

    @Override
    public void clearVerified(String identifier, String purpose) {
        redisTemplate.delete(VERIFIED_KEY + purpose + ":" + identifier);
    }
}
