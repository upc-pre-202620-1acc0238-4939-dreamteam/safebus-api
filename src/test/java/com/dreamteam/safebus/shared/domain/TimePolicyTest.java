package com.dreamteam.safebus.shared.domain;

import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimePolicyTest {

    @Test
    void passengerRequestsShareTheFutureSkewOfTheSharedPolicy() {
        assertEquals(Duration.ofMinutes(5), TimePolicy.MAX_FUTURE_SKEW);
        assertEquals(TimePolicy.MAX_FUTURE_SKEW, PassengerRequestPolicy.MAX_FUTURE_SKEW);
    }
}
