package com.cangshuo.toolbox.auth.config;

import com.cangshuo.toolbox.auth.model.UserAccount;
import com.cangshuo.toolbox.auth.service.AccessTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.junit.jupiter.api.Assertions.*;

class AccessTokenTest {
    private final AuthConfiguration config = new AuthConfiguration();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ZoneOffset.UTC);
    private final javax.crypto.spec.SecretKeySpec key = config.authSigningKey("12".repeat(32));
    private final JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtDecoder decoder = config.jwtDecoder(key, clock);

    @Test void issuedTokenRoundTripsWithoutPrivateUserFields() {
        var response = new AccessTokenService(encoder, clock).issue(new UserAccount(7, "user@example.com", "hash", "用户", 1), "12".repeat(16), "12".repeat(16) + "." + "a".repeat(43), clock.instant().plusSeconds(2592000));
        Jwt jwt = decoder.decode(response.accessToken());
        assertEquals("7", jwt.getSubject());
        assertEquals(900, response.expiresIn());
        assertEquals(clock.instant().plusSeconds(900), response.expiresAt());
        assertFalse(jwt.getClaims().containsKey("email"));
        assertFalse(jwt.getClaims().containsKey("passwordHash"));
        assertFalse(response.toString().contains(response.accessToken()));
    }
    @Test void tamperedSignatureIsRejected() {
        String token = new AccessTokenService(encoder, clock).issue(new UserAccount(1, "a@b.com", "h", "u", 1), "12".repeat(16), "12".repeat(16) + "." + "a".repeat(43), clock.instant().plusSeconds(2592000)).accessToken();
        int signature = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, signature) + (token.charAt(signature) == 'A' ? "B" : "A") + token.substring(signature + 1);
        assertThrows(JwtException.class, () -> decoder.decode(tampered));
    }
    @Test void expiredWrongAudienceWrongIssuerAndMissingExpirationAreRejected() {
        assertThrows(JwtException.class, () -> decoder.decode(custom(AuthConfiguration.ISSUER, AuthConfiguration.AUDIENCE, -1)));
        assertThrows(JwtException.class, () -> decoder.decode(custom(AuthConfiguration.ISSUER, "other", 900)));
        assertThrows(JwtException.class, () -> decoder.decode(custom("other", AuthConfiguration.AUDIENCE, 900)));
        assertThrows(JwtException.class, () -> decoder.decode(custom(AuthConfiguration.ISSUER, AuthConfiguration.AUDIENCE, 0)));
    }
    @Test void configurationRejectsMissingOrShortSecret() {
        assertThrows(IllegalStateException.class, () -> config.authSigningKey(""));
        assertThrows(IllegalStateException.class, () -> config.authSigningKey("short"));
    }
    private String custom(String issuer, String audience, long seconds) {
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject("1").claim("sid", "12".repeat(16)).issuedAt(clock.instant().minusSeconds(2));
        if (seconds != 0) claims.expiresAt(clock.instant().plusSeconds(seconds));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
