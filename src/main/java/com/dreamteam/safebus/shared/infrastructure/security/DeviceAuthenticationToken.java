package com.dreamteam.safebus.shared.infrastructure.security;

import com.dreamteam.safebus.shared.application.CountingSourcePrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

// Holds no credential: the raw key is dropped as soon as the source is resolved
public class DeviceAuthenticationToken extends AbstractAuthenticationToken {

    public static final String ROLE = "DEVICE";

    private final CountingSourcePrincipal principal;

    public DeviceAuthenticationToken(CountingSourcePrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public CountingSourcePrincipal getPrincipal() {
        return principal;
    }
}
