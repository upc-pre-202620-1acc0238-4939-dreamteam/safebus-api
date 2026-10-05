package com.dreamteam.safebus.passenger.infrastructure;

import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.trip.application.PassengerJourneyAccessPort;
import org.springframework.stereotype.Component;

@Component
public class PassengerJourneyAccessAdapter implements PassengerJourneyAccessPort {

    private final PassengerJourneyRepository journeyRepository;

    public PassengerJourneyAccessAdapter(PassengerJourneyRepository journeyRepository) {
        this.journeyRepository = journeyRepository;
    }

    @Override
    public boolean hasActiveJourneyOnBus(Long passengerUserAccountId, Long busId) {
        return journeyRepository.existsByUserAccountIdAndBusIdAndStatus(
            passengerUserAccountId, busId, JourneyStatus.ACTIVE);
    }
}
