package com.cangshuo.toolbox.auth.service;

import com.cangshuo.toolbox.auth.config.AuthConfiguration;
import com.cangshuo.toolbox.auth.model.AuthResponse;
import com.cangshuo.toolbox.auth.model.UserAccount;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService {
    private final JwtEncoder encoder;
    private final Clock clock;
    public AccessTokenService(JwtEncoder encoder, Clock clock) { this.encoder = encoder; this.clock = clock; }

    public AuthResponse issue(UserAccount account, String sessionCode, String refreshToken, java.time.Instant refreshExpiresAt) {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var expires = now.plusSeconds(900);
        var claims = JwtClaimsSet.builder().issuer(AuthConfiguration.ISSUER)
                .audience(List.of(AuthConfiguration.AUDIENCE)).subject(Long.toString(account.id()))
                .issuedAt(now).notBefore(now).expiresAt(expires).claim("sid", sessionCode).id(UUID.randomUUID().toString()).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims));
        return new AuthResponse(token.getTokenValue(), "Bearer", 900, expires, account.publicProfile(), refreshToken, refreshExpiresAt);
    }
}
