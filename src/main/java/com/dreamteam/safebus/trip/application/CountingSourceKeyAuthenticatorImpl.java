package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.shared.application.CountingSourceKeyAuthenticator;
import com.dreamteam.safebus.shared.application.CountingSourcePrincipal;
import com.dreamteam.safebus.trip.domain.model.CountingSourceStatus;
import com.dreamteam.safebus.trip.domain.port.CountingSourceKeys;
import com.dreamteam.safebus.trip.domain.repository.CountingSourceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CountingSourceKeyAuthenticatorImpl implements CountingSourceKeyAuthenticator {

    private final CountingSourceRepository sourceRepository;
    private final CountingSourceKeys keys;

    public CountingSourceKeyAuthenticatorImpl(CountingSourceRepository sourceRepository, CountingSourceKeys keys) {
        this.sourceRepository = sourceRepository;
        this.keys = keys;
    }

    @Override
    public Optional<CountingSourcePrincipal> authenticate(String rawKey) {
        return sourceRepository.findByKeyHashAndStatus(keys.hash(rawKey), CountingSourceStatus.ACTIVE)
            .map(s -> new CountingSourcePrincipal(s.getId(), s.getBusId(), s.getCompanyId()));
    }
}
