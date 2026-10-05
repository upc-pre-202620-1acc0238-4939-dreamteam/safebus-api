package com.dreamteam.safebus.passenger.interfaces.acl;

import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PassengerContextFacade {

    public record ActiveJourneyInfo(Long journeyId, Long busId, Long shiftId) {}

    private final PassengerJourneyRepository journeyRepository;

    public PassengerContextFacade(PassengerJourneyRepository journeyRepository) {
        this.journeyRepository = journeyRepository;
    }

    public Optional<ActiveJourneyInfo> findActiveJourney(Long userAccountId) {
        return journeyRepository.findByUserAccountIdAndStatus(userAccountId, JourneyStatus.ACTIVE)
            .map(j -> new ActiveJourneyInfo(j.getId(), j.getBusId(), j.getShiftId()));
    }
}
