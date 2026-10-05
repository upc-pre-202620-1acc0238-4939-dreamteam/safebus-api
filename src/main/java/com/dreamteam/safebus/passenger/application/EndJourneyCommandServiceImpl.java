package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class EndJourneyCommandServiceImpl implements EndJourneyCommandService {

    private final PassengerJourneyRepository journeyRepository;
    private final Clock clock;

    public EndJourneyCommandServiceImpl(PassengerJourneyRepository journeyRepository, Clock clock) {
        this.journeyRepository = journeyRepository;
        this.clock = clock;
    }

    @Override
    public EndJourneyResult end(EndJourneyCommand cmd) {
        JourneyEndReason reason = parseReason(cmd.reason());

        PassengerJourney journey = journeyRepository.findByIdForUpdate(cmd.journeyId())
            .orElseThrow(() -> new ForbiddenOperationException("JOURNEY_ACCESS_DENIED",
                "journey not found or access denied"));

        if (!journey.getUserAccountId().equals(cmd.userAccountId())) {
            throw new ForbiddenOperationException("JOURNEY_ACCESS_DENIED",
                "journey does not belong to the authenticated passenger");
        }

        journey.end(reason, Instant.now(clock));
        journeyRepository.save(journey);
        return new EndJourneyResult(journey.getId(), journey.getStatus(),
            journey.getEndedAt(), journey.getEndReason());
    }

    private JourneyEndReason parseReason(String reason) {
        if (reason == null) {
            return JourneyEndReason.MANUAL;
        }
        return switch (reason.trim().toUpperCase()) {
            case "MANUAL"   -> JourneyEndReason.MANUAL;
            case "SIGN_OUT" -> JourneyEndReason.SIGN_OUT;
            default -> throw new RuleViolationException("INVALID_END_REASON",
                "reason must be MANUAL or SIGN_OUT");
        };
    }
}
