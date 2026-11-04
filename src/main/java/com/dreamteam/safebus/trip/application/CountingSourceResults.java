package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.trip.domain.model.CountingSource;

final class CountingSourceResults {

    private CountingSourceResults() {}

    static CountingSourceResult of(CountingSource source) {
        return new CountingSourceResult(source.getId(), source.getBusId(), source.getLabel(),
            source.getStatus().name(), source.getCreatedAt(), source.getRotatedAt(), source.getRevokedAt());
    }
}
