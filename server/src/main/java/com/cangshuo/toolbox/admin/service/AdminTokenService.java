package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminAccount;
import com.cangshuo.toolbox.admin.model.AdminAuthResponse;
import com.cangshuo.toolbox.auth.config.AuthConfiguration;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AdminTokenService {
    public static final long MIN_TTL_SECONDS = 300;
    public static final long MAX_TTL_SECONDS = 86400;
    private final JwtEncoder encoder;
    private final Clock clock;
    private final long ttlSeconds;

    public AdminTokenService(JwtEncoder encoder, Clock clock,
                             @Value("${toolbox.admin.token-ttl-seconds:3600}") long ttlSeconds) {
        if (ttlSeconds < MIN_TTL_SECONDS || ttlSeconds > MAX_TTL_SECONDS) {
            throw new IllegalStateException("ADMIN_TOKEN_TTL_SECONDS must be between 300 and 86400");
        }
        this.encoder = encoder; this.clock = clock; this.ttlSeconds = ttlSeconds;
    }

    public AdminAuthResponse issue(AdminAccount account) {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var expires = now.plusSeconds(ttlSeconds);
        var claims = JwtClaimsSet.builder().issuer(AuthConfiguration.ISSUER)
                .audience(List.of(AuthConfiguration.AUDIENCE)).subject(Long.toString(account.id()))
                .issuedAt(now).notBefore(now).expiresAt(expires)
                .claim("kind", AuthConfiguration.ADMIN_KIND)
                .claim("role", account.roleCode())
                .claim("username", account.username())
                .id(UUID.randomUUID().toString()).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims));
        return new AdminAuthResponse(token.getTokenValue(), "Bearer", ttlSeconds, expires, account.publicProfile());
    }
}
