package com.cangshuo.toolbox.auth.config;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Maps administrator tokens to ROLE_ authorities; user tokens never reach this converter. */
@Component
public class AdminJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (!AuthConfiguration.ADMIN_KIND.equals(jwt.getClaimAsString("kind"))
                || role == null || !role.matches("[A-Z][A-Z0-9_]{2,31}")) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
        }
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)),
                jwt.getClaimAsString("username"));
    }
}
