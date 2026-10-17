package com.fintrack.auth.service.impl;

import com.fintrack.auth.dto.request.ForgotPasswordRequest;
import com.fintrack.auth.dto.request.LoginRequest;
import com.fintrack.auth.dto.request.RefreshTokenRequest;
import com.fintrack.auth.dto.request.RegisterRequest;
import com.fintrack.auth.dto.request.ResetPasswordRequest;
import com.fintrack.auth.dto.request.UpdateCurrencyRequest;
import com.fintrack.auth.dto.request.UpdateUserProfileRequest;
import com.fintrack.auth.dto.response.AuthResponse;
import com.fintrack.auth.dto.response.UserResponse;
import com.fintrack.auth.mail.PasswordResetMailer;
import com.fintrack.auth.model.PasswordResetToken;
import com.fintrack.auth.model.RefreshToken;
import com.fintrack.auth.model.User;
import com.fintrack.auth.model.enums.Role;
import com.fintrack.auth.repository.PasswordResetTokenRepository;
import com.fintrack.auth.repository.RefreshTokenRepository;
import com.fintrack.auth.repository.UserRepository;
import com.fintrack.auth.service.AuthService;
import com.fintrack.auth.service.JwtService;
import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetMailer passwordResetMailer;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${fintrack.jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    @Value("${app.password-reset.token-ttl-minutes:30}")
    private long resetTokenTtlMinutes;

    @Value("${app.password-reset.request-cooldown-seconds:60}")
    private long resetRequestCooldownSeconds;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
            throw new AppException(ErrorCode.DUPLICATE_USERNAME);
        }

        if (userRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new AppException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(Role.ADMIN))
                .currency(request.getCurrency())
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: username={}, email={}", savedUser.getUsername(), savedUser.getEmail());

        return toUserResponse(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        if (storedToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = getUserOrThrow(storedToken.getUserId());

        refreshTokenRepository.delete(storedToken);
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public void logout(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
        log.info("User {} logged out — all refresh tokens revoked", userId);
    }

    @Override
    @Cacheable(value = "userProfiles", key = "#userId")
    public UserResponse getProfile(String userId) {
        return toUserResponse(getUserOrThrow(userId));
    }

    @Override
    @Transactional
    @CacheEvict(value = "userProfiles", key = "#userId")
    public UserResponse updateProfile(String userId, UpdateUserProfileRequest request) {
        User user = getUserOrThrow(userId);

        if (!user.getEmail().equalsIgnoreCase(request.getEmail())
                && userRepository.existsByEmailIgnoreCaseAndDeletedFalse(request.getEmail())) {
            throw new AppException(ErrorCode.DUPLICATE_EMAIL);
        }

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setCurrency(request.getCurrency());

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.DUPLICATE_EMAIL);
        }
        log.info("User profile updated: userId={}", userId);

        return toUserResponse(savedUser);
    }

    @Override
    @Transactional
    @CacheEvict(value = "userProfiles", key = "#userId")
    public UserResponse updateCurrency(String userId, UpdateCurrencyRequest request) {
        User user = getUserOrThrow(userId);

        user.setCurrency(request.getCurrency());

        User savedUser = userRepository.save(user);
        log.info("User currency updated: userId={}, currency={}", userId, request.getCurrency());

        return toUserResponse(savedUser);
    }

    private User getUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = createAndSaveRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .user(toUserResponse(user))
                .build();
    }

    private String createAndSaveRefreshToken(User user) {
        String tokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .userId(user.getId())
                .expiryDate(Instant.now().plusSeconds(60L * 60 * 24 * refreshTokenExpiryDays))
                .build();

        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .currency(user.getCurrency())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameAndDeletedFalse(request.getEmailOrUsername())
                .or(() -> userRepository.findByEmailAndDeletedFalse(request.getEmailOrUsername()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim();

        // Unknown address: do nothing, and say nothing — the controller answers 200 either way.
        userRepository.findByEmailIgnoreCaseAndDeletedFalse(email).ifPresent(user -> {
            Instant now = Instant.now();

            // Per-account throttle: stops the endpoint being used to mail-bomb a victim.
            if (passwordResetTokenRepository.existsByUserIdAndCreatedAtAfter(
                    user.getId(), now.minusSeconds(resetRequestCooldownSeconds))) {
                log.info("Password reset throttled: userId={}", user.getId());
                return;
            }

            // Only the newest link is valid.
            passwordResetTokenRepository.deleteByUserId(user.getId());

            byte[] bytes = new byte[32];
            SECURE_RANDOM.nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .tokenHash(hashToken(rawToken))
                    .userId(user.getId())
                    .createdAt(now)
                    .expiryDate(now.plus(Duration.ofMinutes(resetTokenTtlMinutes)))
                    .build());

            String link = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + rawToken;
            passwordResetMailer.sendResetLink(user.getEmail(), user.getFullName(), link, resetTokenTtlMinutes);
            log.info("Password reset requested: userId={}", user.getId());
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken stored = passwordResetTokenRepository
                .findByTokenHash(hashToken(request.getToken()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        if (stored.getExpiryDate().isBefore(Instant.now())) {
            passwordResetTokenRepository.delete(stored);
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = getUserOrThrow(stored.getUserId());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Single use, and invalidate every outstanding link and session: whoever held the old
        // password (or a stolen refresh token) must not stay signed in.
        passwordResetTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.deleteByUserId(user.getId());
        log.info("Password reset completed: userId={}", user.getId());
    }

    private static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
