package com.cangshuo.toolbox.auth.config;

import com.cangshuo.toolbox.auth.service.RefreshTokenService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

@Component
public class SessionJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final RefreshTokenService sessions;
    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
    public SessionJwtConverter(RefreshTokenService sessions) { this.sessions = sessions; }
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!sessions.active(jwt.getClaimAsString("sid"), jwt.getSubject())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
        }
        return delegate.convert(jwt);
    }
}
