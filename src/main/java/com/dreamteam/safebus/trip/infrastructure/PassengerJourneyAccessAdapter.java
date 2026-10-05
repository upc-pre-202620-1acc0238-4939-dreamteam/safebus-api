package com.dreamteam.safebus.trip.infrastructure;

import com.dreamteam.safebus.trip.application.PassengerJourneyAccessPort;
import org.springframework.stereotype.Component;

// Placeholder: will be replaced by an adapter from the passenger context
@Component
public class PassengerJourneyAccessAdapter implements PassengerJourneyAccessPort {

    @Override
    public boolean hasActiveJourneyOnBus(Long passengerUserAccountId, Long busId) {
        return false;
    }
}
