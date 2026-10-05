package com.dreamteam.safebus.passenger.infrastructure;

import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.trip.application.ShiftJourneyClosurePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class ShiftJourneyClosureAdapter implements ShiftJourneyClosurePort {

    private final PassengerJourneyRepository journeyRepository;

    public ShiftJourneyClosureAdapter(PassengerJourneyRepository journeyRepository) {
        this.journeyRepository = journeyRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int endJourneysOfShift(Long shiftId, Instant now) {
        List<PassengerJourney> active =
            journeyRepository.findByShiftIdAndStatus(shiftId, JourneyStatus.ACTIVE);
        int ended = 0;
        for (PassengerJourney journey : active) {
            if (journey.end(JourneyEndReason.SHIFT_CLOSED, now)) {
                journeyRepository.save(journey);
                ended++;
            }
        }
        return ended;
    }
}
