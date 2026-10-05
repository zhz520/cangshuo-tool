package com.cangshuo.toolbox.auth.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Routes administrator tokens to the admin converter and everything else to the user session converter. */
@Component
public class CombinedJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final SessionJwtConverter sessions;
    private final AdminJwtConverter admins;
    public CombinedJwtConverter(SessionJwtConverter sessions, AdminJwtConverter admins) {
        this.sessions = sessions; this.admins = admins;
    }
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        return AuthConfiguration.ADMIN_KIND.equals(jwt.getClaimAsString("kind"))
                ? admins.convert(jwt) : sessions.convert(jwt);
    }
}
