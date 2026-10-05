package com.cangshuo.toolbox.auth.service;

import com.cangshuo.toolbox.auth.model.*;
import com.cangshuo.toolbox.auth.repository.*;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    private final RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final AccessTokenService access = mock(AccessTokenService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ZoneOffset.UTC);
    private final RefreshTokenService service = new RefreshTokenService(sessions, users, access, clock);
    private final String code = "12".repeat(16);
    private final String token = code + "." + "a".repeat(43);
    private final UserAccount user = new UserAccount(7,"a@b.com","hash","u",1);
    private RefreshSession session(Instant expires, Instant revoked) { return new RefreshSession(1,code,7,expires,revoked); }

    @Test void startStoresOnlyHashAndUsesThirtyDayAbsoluteExpiry() {
        when(sessions.create(anyString(), eq(7L), any(), any())).thenAnswer(call ->
                new RefreshSession(1,call.getArgument(0),7,call.getArgument(2),null));
        when(access.issue(eq(user), anyString(), anyString(), any())).thenAnswer(call -> {
            String refresh = call.getArgument(2);
            assertTrue(refresh.matches("[0-9a-f]{32}\\.[A-Za-z0-9_-]{43}"));
            assertEquals(clock.instant().plusSeconds(2592000), call.getArgument(3));
            verify(sessions).insertToken(1, RefreshTokenService.hash(refresh));
            return null;
        });
        service.start(user);
    }
    @Test void refreshConsumesOldTokenAndStoresNewHashWithoutExtendingExpiry() {
        var session = session(clock.instant().plusSeconds(1000),null);
        when(sessions.lock(code)).thenReturn(Optional.of(session));
        when(sessions.consumed(1,RefreshTokenService.hash(token))).thenReturn(Optional.of(false));
        when(users.findById(7)).thenReturn(Optional.of(user));
        service.refresh(token);
        verify(sessions).consume(1,RefreshTokenService.hash(token),clock.instant());
        verify(sessions).insertToken(eq(1L), anyString());
        verify(access).issue(eq(user),eq(code),argThat(newToken -> !newToken.equals(token)),eq(session.expiresAt()));
    }
    @Test void consumedTokenRevokesFamilyButGuessedTokenDoesNot() {
        when(sessions.lock(code)).thenReturn(Optional.of(session(clock.instant().plusSeconds(1000),null)));
        when(sessions.consumed(1,RefreshTokenService.hash(token))).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.refresh(token));
        verify(sessions,never()).revoke(anyLong(),any());
        when(sessions.consumed(1,RefreshTokenService.hash(token))).thenReturn(Optional.of(true));
        assertThrows(ApiException.class, () -> service.refresh(token));
        verify(sessions).revoke(1,clock.instant());
        verifyNoInteractions(access);
    }
    @Test void expiredRevokedDisabledAndMalformedTokensAreRejected() {
        when(sessions.lock(code)).thenReturn(Optional.of(session(clock.instant(),null)));
        when(sessions.consumed(1,RefreshTokenService.hash(token))).thenReturn(Optional.of(false));
        assertThrows(ApiException.class, () -> service.refresh(token));
        when(sessions.lock(code)).thenReturn(Optional.of(session(clock.instant().plusSeconds(1000),clock.instant())));
        assertThrows(ApiException.class, () -> service.refresh(token));
        when(sessions.lock(code)).thenReturn(Optional.of(session(clock.instant().plusSeconds(1000),null)));
        when(users.findById(7)).thenReturn(Optional.of(new UserAccount(7,"a@b.com","hash","u",0)));
        assertThrows(ApiException.class, () -> service.refresh(token));
        assertThrows(ApiException.class, () -> service.refresh("invalid"));
        verifyNoInteractions(access);
    }
    @Test void logoutAcceptsConsumedTokenAndIsIdempotent() {
        when(sessions.lock(code)).thenReturn(Optional.of(session(clock.instant().plusSeconds(1000),null)));
        when(sessions.consumed(1,RefreshTokenService.hash(token))).thenReturn(Optional.of(true));
        service.logout(token); service.logout(token);
        verify(sessions,times(2)).revoke(1,clock.instant());
    }
    @Test void unknownLogoutDoesNotRevokeKnownFamily() {
        when(sessions.lock(code)).thenReturn(Optional.of(session(clock.instant().plusSeconds(1000),null)));
        when(sessions.consumed(anyLong(),anyString())).thenReturn(Optional.empty());
        service.logout(token);
        verify(sessions,never()).revoke(anyLong(),any());
    }
}
