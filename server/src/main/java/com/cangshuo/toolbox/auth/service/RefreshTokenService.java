package com.cangshuo.toolbox.auth.service;

import com.cangshuo.toolbox.auth.model.AuthResponse;
import com.cangshuo.toolbox.auth.model.UserAccount;
import com.cangshuo.toolbox.auth.repository.RefreshSessionRepository;
import com.cangshuo.toolbox.auth.repository.UserAccountRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private final RefreshSessionRepository sessions;
    private final UserAccountRepository users;
    private final AccessTokenService access;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public RefreshTokenService(RefreshSessionRepository sessions, UserAccountRepository users, AccessTokenService access, Clock clock) {
        this.sessions = sessions; this.users = users; this.access = access; this.clock = clock;
    }

    @Transactional
    public AuthResponse start(UserAccount user) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        String code = UUID.randomUUID().toString().replace("-", "");
        var session = sessions.create(code, user.id(), now.plus(30, ChronoUnit.DAYS), now);
        String token = newToken(code);
        sessions.insertToken(session.id(), hash(token));
        return access.issue(user, code, token, session.expiresAt());
    }

    // Replayed-token revocation must commit even though the public operation returns 401.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String token) {
        String code = selector(token);
        var session = sessions.lock(code).orElseThrow(RefreshTokenService::unauthenticated);
        var consumed = sessions.consumed(session.id(), hash(token));
        if (consumed.isEmpty()) throw unauthenticated();
        Instant now = clock.instant();
        if (Boolean.TRUE.equals(consumed.get())) {
            sessions.revoke(session.id(), now);
            throw unauthenticated();
        }
        if (session.revokedAt() != null || !session.expiresAt().isAfter(now)) throw unauthenticated();
        var user = users.findById(session.userId()).filter(account -> account.status() == 1)
                .orElseThrow(RefreshTokenService::unauthenticated);
        String replacement = newToken(code);
        sessions.consume(session.id(), hash(token), now);
        sessions.insertToken(session.id(), hash(replacement));
        return access.issue(user, code, replacement, session.expiresAt());
    }

    @Transactional
    public void logout(String token) {
        String code = selector(token);
        sessions.lock(code).ifPresent(session -> {
            if (sessions.consumed(session.id(), hash(token)).isPresent()) sessions.revoke(session.id(), clock.instant());
        });
    }

    public boolean active(String code, String subject) {
        if (code == null || !code.matches("[0-9a-f]{32}")) return false;
        try { return sessions.active(code, Long.parseLong(subject), clock.instant()); }
        catch (NumberFormatException exception) { return false; }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void removeExpiredSessions() { sessions.deleteExpired(clock.instant()); }

    private String newToken(String code) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return code + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static String selector(String token) {
        if (token == null || !token.matches("[0-9a-f]{32}\\.[A-Za-z0-9_-]{43}")) throw unauthenticated();
        return token.substring(0, 32);
    }
    private static ApiException unauthenticated() { return new ApiException(ApiError.UNAUTHENTICATED); }
}
