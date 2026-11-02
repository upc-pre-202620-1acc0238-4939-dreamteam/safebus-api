package com.dreamteam.safebus.passenger.interfaces.acl;

import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class PassengerContextFacade {

    public record ActiveJourneyInfo(Long journeyId, Long busId, Long shiftId) {}

    public record JourneyView(Long journeyId, Long busId, Long shiftId, String status,
                              Instant startedAt, Instant endedAt) {}

    private final PassengerJourneyRepository journeyRepository;

    public PassengerContextFacade(PassengerJourneyRepository journeyRepository) {
        this.journeyRepository = journeyRepository;
    }

    public Optional<ActiveJourneyInfo> findActiveJourney(Long userAccountId) {
        return journeyRepository.findByUserAccountIdAndStatus(userAccountId, JourneyStatus.ACTIVE)
            .map(j -> new ActiveJourneyInfo(j.getId(), j.getBusId(), j.getShiftId()));
    }

    // Another account's journey and an unknown id are indistinguishable: both give an empty result.
    public Optional<JourneyView> findJourneyOfPassenger(Long userAccountId, Long journeyId) {
        return journeyRepository.findByIdAndUserAccountId(journeyId, userAccountId)
            .map(j -> new JourneyView(j.getId(), j.getBusId(), j.getShiftId(), j.getStatus().name(),
                j.getStartedAt(), j.getEndedAt()));
    }
}
