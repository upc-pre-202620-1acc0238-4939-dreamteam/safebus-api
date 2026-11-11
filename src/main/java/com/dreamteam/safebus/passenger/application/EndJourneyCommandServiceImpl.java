package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.model.SeparationSummary;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// One locked row and no unique-key insert: there is nothing to retry, so no orchestrator is needed.
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class EndJourneyCommandServiceImpl implements EndJourneyCommandService {

    static final String ACCESS_DENIED = "journey not found or access denied";

    private final PassengerJourneyRepository journeyRepository;
    private final Clock clock;

    public EndJourneyCommandServiceImpl(PassengerJourneyRepository journeyRepository, Clock clock) {
        this.journeyRepository = journeyRepository;
        this.clock = clock;
    }

    @Override
    public EndJourneyResult end(EndJourneyCommand cmd) {
        JourneyEndReason reason = parseReason(cmd.reason());
        SeparationSummary summary =
            SeparationSummary.ofNullable(cmd.separationDistanceMeters(), cmd.separationDurationSeconds());
        if (summary != null && reason != JourneyEndReason.AUTOMATIC_SEPARATION) {
            throw new RuleViolationException("INVALID_END_SUMMARY",
                "a separation summary is only valid with reason AUTOMATIC_SEPARATION");
        }

        PassengerJourney journey = journeyRepository.findByIdForUpdate(cmd.journeyId())
            .filter(found -> found.getUserAccountId().equals(cmd.userAccountId()))
            .orElseThrow(() -> new ForbiddenOperationException("JOURNEY_ACCESS_DENIED", ACCESS_DENIED));

        if (journey.end(reason, cmd.endedAt(), summary, Instant.now(clock))) {
            journeyRepository.save(journey);
        }
        return new EndJourneyResult(journey.getId(), journey.getStatus(), journey.getEndedAt(),
            journey.getEndReason(), journey.getSeparationSummary());
    }

    private JourneyEndReason parseReason(String reason) {
        if (reason == null) {
            return JourneyEndReason.MANUAL;
        }
        return switch (reason.trim().toUpperCase()) {
            case "MANUAL"               -> JourneyEndReason.MANUAL;
            case "SIGN_OUT"             -> JourneyEndReason.SIGN_OUT;
            case "AUTOMATIC_SEPARATION" -> JourneyEndReason.AUTOMATIC_SEPARATION;
            default -> throw new RuleViolationException("INVALID_END_REASON",
                "reason must be MANUAL, SIGN_OUT or AUTOMATIC_SEPARATION");
        };
    }
}
