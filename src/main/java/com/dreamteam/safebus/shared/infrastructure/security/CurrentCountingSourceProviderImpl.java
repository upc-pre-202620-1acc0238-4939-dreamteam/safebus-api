package com.dreamteam.safebus.shared.infrastructure.security;

import com.dreamteam.safebus.shared.application.CountingSourcePrincipal;
import com.dreamteam.safebus.shared.application.CurrentCountingSourceProvider;
import com.dreamteam.safebus.shared.domain.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentCountingSourceProviderImpl implements CurrentCountingSourceProvider {

    @Override
    public CountingSourcePrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof DeviceAuthenticationToken device)) {
            throw new UnauthorizedException("Not authenticated");
        }
        return device.getPrincipal();
    }
}
