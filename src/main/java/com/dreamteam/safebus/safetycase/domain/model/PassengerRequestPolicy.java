package com.dreamteam.safebus.safetycase.domain.model;

import java.time.Duration;

public final class PassengerRequestPolicy {

    public static final int REQUIRED_CONTRIBUTORS = 3;
    public static final Duration WINDOW = Duration.ofMinutes(5);
    public static final Duration MAX_REQUEST_AGE = Duration.ofMinutes(5);
    public static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    public static final int MAX_MESSAGE_CODE_POINTS = 500;

    private PassengerRequestPolicy() {}
}
