package com.dreamteam.safebus.shared.domain;

import java.time.Duration;

public final class TimePolicy {

    public static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private TimePolicy() {}
}
