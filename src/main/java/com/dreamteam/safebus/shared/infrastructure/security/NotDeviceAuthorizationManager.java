package com.dreamteam.safebus.shared.infrastructure.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

// Authenticated callers other than counting-source devices
public class NotDeviceAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
                                           RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        boolean allowed = auth != null
            && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken)
            && auth.getAuthorities().stream()
                .noneMatch(a -> ("ROLE_" + DeviceAuthenticationToken.ROLE).equals(a.getAuthority()));
        return new AuthorizationDecision(allowed);
    }
}
