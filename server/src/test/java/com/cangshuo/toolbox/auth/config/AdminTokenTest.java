package com.cangshuo.toolbox.auth.config;

import com.cangshuo.toolbox.admin.model.AdminAccount;
import com.cangshuo.toolbox.admin.service.AdminTokenService;
import com.cangshuo.toolbox.auth.model.UserAccount;
import com.cangshuo.toolbox.auth.service.AccessTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminTokenTest {
    private final AuthConfiguration config = new AuthConfiguration();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ZoneOffset.UTC);
    private final SecretKeySpec key = config.authSigningKey("ab".repeat(32));
    private final JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtDecoder decoder = config.jwtDecoder(key, clock);
    private final AdminTokenService service = new AdminTokenService(encoder, clock, 3600);

    @Test void issuedAdminTokenRoundTripsWithKindRoleAndUsername() {
        var response = service.issue(new AdminAccount(3, "root", "hash", "管理员", "SUPER_ADMIN", 1));
        Jwt jwt = decoder.decode(response.accessToken());
        assertEquals("3", jwt.getSubject());
        assertEquals(AuthConfiguration.ADMIN_KIND, jwt.getClaimAsString("kind"));
        assertEquals("SUPER_ADMIN", jwt.getClaimAsString("role"));
        assertEquals("root", jwt.getClaimAsString("username"));
        assertEquals(3600, response.expiresIn());
        assertEquals(clock.instant().plusSeconds(3600), response.expiresAt());
        assertFalse(response.toString().contains(response.accessToken()));
        assertFalse(new AdminAccount(3, "root", "hash", "管理员", "SUPER_ADMIN", 1).toString().contains("hash"));
    }

    @Test void userTokensStillRoundTripAndMalformedAdminClaimsAreRejected() {
        var user = new AccessTokenService(encoder, clock).issue(new UserAccount(7, "user@example.com", "h", "u", 1),
                "12".repeat(16), "12".repeat(16) + "." + "a".repeat(43), clock.instant().plusSeconds(2592000)).accessToken();
        assertEquals("7", decoder.decode(user).getSubject());
        assertThrows(JwtException.class, () -> decoder.decode(admin(null, 600)));
        assertThrows(JwtException.class, () -> decoder.decode(admin("admin", 86402)));
        assertThrows(JwtException.class, () -> decoder.decode(admin("lowercase", 600)));
    }

    @Test void ttlOutsidePolicyFailsFast() {
        assertThrows(IllegalStateException.class, () -> new AdminTokenService(encoder, clock, 299));
        assertThrows(IllegalStateException.class, () -> new AdminTokenService(encoder, clock, 86401));
        assertNotNull(new AdminTokenService(encoder, clock, 300));
        assertNotNull(new AdminTokenService(encoder, clock, 86400));
    }

    private String admin(String role, long ttl) {
        var builder = JwtClaimsSet.builder().issuer(AuthConfiguration.ISSUER)
                .audience(List.of(AuthConfiguration.AUDIENCE)).subject("3")
                .issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(ttl))
                .claim("kind", AuthConfiguration.ADMIN_KIND);
        if (role != null) builder.claim("role", role);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
    }
}
