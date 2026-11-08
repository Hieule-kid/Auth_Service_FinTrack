package com.fintrack.auth.service.impl;

import com.fintrack.auth.dto.request.LoginRequest;
import com.fintrack.auth.dto.request.RefreshTokenRequest;
import com.fintrack.auth.dto.request.RegisterRequest;
import com.fintrack.auth.model.RefreshToken;
import com.fintrack.auth.model.User;
import com.fintrack.auth.model.enums.Role;
import com.fintrack.auth.repository.RefreshTokenRepository;
import com.fintrack.auth.repository.UserRepository;
import com.fintrack.auth.service.JwtService;
import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthServiceImpl authService;

    private static RegisterRequest registerRequest(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Alice Nguyen");
        req.setUsername("alice");
        req.setEmail(email);
        req.setPassword("secret123");
        return req;
    }

    private static User user(String id, boolean deleted) {
        User user = User.builder()
                .username("alice")
                .email("alice@example.com")
                .password("hash")
                .roles(Set.of(Role.USER))
                .build();
        user.setId(id);
        user.setDeleted(deleted);
        return user;
    }

    private static RefreshToken token(String userId, Instant expiry) {
        return RefreshToken.builder().token("rt-1").userId(userId).expiryDate(expiry).build();
    }

    @Test
    void register_assignsUserRoleNotAdmin() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(registerRequest("alice@example.com"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRoles()).containsExactly(Role.USER);
    }

    @Test
    void register_normalizesEmailAndChecksDuplicatesIgnoringCase() {
        when(userRepository.existsByEmailIgnoreCaseAndDeletedFalse("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("  Alice@Example.COM ")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void register_constraintViolationOnSaveBecomesDuplicateEmail() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uk_email"));

        assertThatThrownBy(() -> authService.register(registerRequest("alice@example.com")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
    }

    @Test
    void login_findsUserByEmailIgnoringCase() {
        User alice = user("u-1", false);
        when(userRepository.findByUsernameAndDeletedFalse("ALICE@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedFalse("ALICE@example.com")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(alice)).thenReturn("jwt");

        LoginRequest req = new LoginRequest();
        req.setEmailOrUsername("ALICE@example.com");
        req.setPassword("secret123");

        assertThat(authService.login(req).getAccessToken()).isEqualTo("jwt");
    }

    @Test
    void refresh_rejectsTokenOfDeletedUserAndRevokesIt() {
        RefreshToken stored = token("u-1", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.of(stored));
        when(userRepository.findByIdAndDeletedFalse("u-1")).thenReturn(Optional.empty());

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("rt-1");

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
        verify(refreshTokenRepository).delete(stored);
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void refresh_rotatesTokenForActiveUser() {
        User alice = user("u-1", false);
        RefreshToken stored = token("u-1", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.of(stored));
        when(userRepository.findByIdAndDeletedFalse("u-1")).thenReturn(Optional.of(alice));
        when(jwtService.generateAccessToken(alice)).thenReturn("jwt");

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("rt-1");

        assertThat(authService.refresh(req).getRefreshToken()).isNotEqualTo("rt-1");
        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    void refresh_expiredTokenIsDeletedAndRejected() {
        RefreshToken stored = token("u-1", Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.of(stored));

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("rt-1");

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED));
        verify(userRepository, never()).findByIdAndDeletedFalse(anyString());
    }
}
