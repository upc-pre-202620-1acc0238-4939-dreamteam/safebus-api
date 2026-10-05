package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class StartJourneyCommandServiceImpl implements StartJourneyCommandService {

    private final StartJourneyWriter writer;

    public StartJourneyCommandServiceImpl(StartJourneyWriter writer) {
        this.writer = writer;
    }

    @Override
    public StartJourneyResult start(StartJourneyCommand command) {
        try {
            return writer.write(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            try {
                return writer.write(command);
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException retry) {
                throw new ConflictException("ACTIVE_JOURNEY_EXISTS",
                    "passenger already has an active journey");
            }
        }
    }
}
