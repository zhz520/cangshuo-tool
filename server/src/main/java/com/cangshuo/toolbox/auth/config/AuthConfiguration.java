package com.cangshuo.toolbox.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class AuthConfiguration {
    public static final String ISSUER = "https://toolapi.zhzgo.cn";
    public static final String AUDIENCE = "cangshuo-toolbox";
    /** Token kind claim for administrator access tokens; user tokens omit the claim entirely. */
    public static final String ADMIN_KIND = "admin";

    @Bean Clock authClock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean SecretKeySpec authSigningKey(@Value("${toolbox.auth.jwt-secret}") String secret) {
        if (!secret.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("JWT_SECRET must be a randomly generated 64-character hex secret");
        }
        return new SecretKeySpec(HexFormat.of().parseHex(secret), "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKeySpec authSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(authSigningKey));
    }

    @Bean JwtDecoder jwtDecoder(SecretKeySpec authSigningKey, Clock authClock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(authSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(authClock);
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            String subject = jwt.getSubject();
            boolean common = subject != null && subject.matches("[1-9][0-9]{0,18}")
                    && jwt.getAudience().contains(AUDIENCE) && jwt.getExpiresAt() != null
                    && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(authClock.instant())
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt());
            if (!common) return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            boolean valid;
            if (ADMIN_KIND.equals(jwt.getClaimAsString("kind"))) {
                String role = jwt.getClaimAsString("role");
                valid = role != null && role.matches("[A-Z][A-Z0-9_]{2,31}")
                        && jwt.getExpiresAt().isBefore(jwt.getIssuedAt().plusSeconds(86401));
            } else {
                String sid = jwt.getClaimAsString("sid");
                valid = sid != null && sid.matches("[0-9a-f]{32}")
                        && jwt.getExpiresAt().isBefore(jwt.getIssuedAt().plusSeconds(901));
            }
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                timestamps, new JwtIssuerValidator(ISSUER), claims)));
        return decoder;
    }
}
