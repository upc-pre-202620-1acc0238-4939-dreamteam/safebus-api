package com.dreamteam.safebus.shared.infrastructure.security;

import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProviderImpl implements CurrentUserProvider {

    @Override
    public AuthenticatedUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new UnauthorizedException("Not authenticated");
        }
        Jwt jwt = jwtAuth.getToken();
        Long userId = Long.parseLong(jwt.getSubject());
        String role = jwt.getClaimAsString("role");
        Number companyIdNum = jwt.getClaim("companyId");
        Long companyId = companyIdNum != null ? companyIdNum.longValue() : null;
        return new AuthenticatedUser(userId, role, companyId);
    }
}
