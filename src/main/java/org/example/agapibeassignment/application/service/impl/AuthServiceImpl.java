package org.example.agapibeassignment.application.service.impl;

import org.example.agapibeassignment.application.common.exception.BusinessException;
import org.example.agapibeassignment.application.common.exception.ErrorCode;
import org.example.agapibeassignment.application.entity.Role;
import org.example.agapibeassignment.application.entity.User;
import org.example.agapibeassignment.application.repository.UserRepository;
import org.example.agapibeassignment.application.service.AuthService;
import org.example.agapibeassignment.application.service.OtpService;
import org.example.agapibeassignment.infrastructure.security.JwtTokenProvider;
import org.example.agapibeassignment.rest.request.*;
import org.example.agapibeassignment.rest.response.AuthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final Pattern EMAIL_RE = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_RE = Pattern.compile("^\\+?[0-9]{9,15}$");
    private static final String DENYLIST = "jwt:denylist:";
    private static final String PENDING = "pending_reg:";
    private static final String PURPOSE = "REGISTER";
    private static final BigDecimal INIT_BALANCE = new BigDecimal("10000000.00");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwt;
    private final OtpService otpService;
    private final RedisTemplate<String, Object> redis;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwt, OtpService otpService, RedisTemplate<String, Object> redis) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.otpService = otpService;
        this.redis = redis;
    }

    @Override
    public void register(RegisterRequest req) {
        String id = req.getIdentifier().trim();
        IdType type = detectType(id);
        if (type == IdType.EMAIL && userRepository.existsByEmail(id))
            throw new BusinessException(ErrorCode.AUTH_USER_ALREADY_EXISTS);
        if (type == IdType.PHONE && userRepository.existsByPhone(id))
            throw new BusinessException(ErrorCode.AUTH_USER_ALREADY_EXISTS);

        redis.opsForValue().set(PENDING + id, type.name() + "|" + passwordEncoder.encode(req.getPassword()), 10, TimeUnit.MINUTES);
        otpService.generateAndSend(id, PURPOSE);
    }

    @Override
    @Transactional
    public AuthResponse verifyOtpAndCreateUser(VerifyOtpRequest req) {
        String id = req.getIdentifier().trim();
        if (!otpService.verify(id, PURPOSE, req.getOtpCode()))
            throw new BusinessException(ErrorCode.AUTH_INVALID_OTP);

        Object pending = redis.opsForValue().get(PENDING + id);
        if (pending == null)
            throw new BusinessException(ErrorCode.AUTH_INVALID_OTP, "Registration expired. Please register again.");

        String[] p = pending.toString().split("\\|", 2);
        IdType type = IdType.valueOf(p[0]);

        User user = User.builder().passwordHash(p[1]).balance(INIT_BALANCE).role(Role.USER).build();
        if (type == IdType.EMAIL) { user.setEmail(id); user.setEmailVerified(true); }
        else { user.setPhone(id); user.setPhoneVerified(true); }
        user = userRepository.save(user);

        redis.delete(PENDING + id);
        otpService.clearVerified(id, PURPOSE);
        log.info("User registered: id={}", user.getId());
        return buildResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest req) {
        String id = req.getIdentifier().trim();
        IdType type = detectType(id);
        User user = findUser(id, type);
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        if (!"ACTIVE".equals(user.getStatus()))
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Account is inactive");
        log.info("User logged in: id={}", user.getId());
        return buildResponse(user);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest req) {
        String token = req.getRefreshToken();
        if (!jwt.isTokenValid(token)) throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        if (!"refresh".equals(jwt.getTokenType(token)))
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "Not a refresh token");
        if (Boolean.TRUE.equals(redis.hasKey(DENYLIST + token)))
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "Token has been revoked");

        User user = userRepository.findById(jwt.getUserIdFromToken(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
        redis.opsForValue().set(DENYLIST + token, "revoked", jwt.getRemainingTtlMs(token), TimeUnit.MILLISECONDS);
        return buildResponse(user);
    }

    @Override
    public void logout(String header) {
        if (header == null || !header.startsWith("Bearer ")) return;
        String token = header.substring(7);
        if (jwt.isTokenValid(token))
            redis.opsForValue().set(DENYLIST + token, "revoked", jwt.getRemainingTtlMs(token), TimeUnit.MILLISECONDS);
    }

    private IdType detectType(String id) {
        if (EMAIL_RE.matcher(id).matches()) return IdType.EMAIL;
        if (PHONE_RE.matcher(id).matches()) return IdType.PHONE;
        throw new BusinessException(ErrorCode.AUTH_INVALID_IDENTIFIER);
    }

    private User findUser(String id, IdType type) {
        return (type == IdType.EMAIL ? userRepository.findByEmail(id) : userRepository.findByPhone(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
    }

    private AuthResponse buildResponse(User user) {
        return AuthResponse.builder()
                .accessToken(jwt.generateAccessToken(user.getId(), user.getRole().name()))
                .refreshToken(jwt.generateRefreshToken(user.getId()))
                .tokenType("Bearer").expiresIn(jwt.getAccessTokenExpiryMs() / 1000)
                .role(user.getRole().name()).build();
    }

    private enum IdType { EMAIL, PHONE }
}
