package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.trip.domain.exceptions.SequenceGapException;
import com.dreamteam.safebus.trip.domain.model.OccupancyAssessment;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

// Deliberately not transactional: marks written by the writer must survive the exceptions thrown here
@Service
public class OccupancyCommandServiceImpl implements OccupancyCommandService {

    private final OccupancyWriter writer;
    private final FleetContextFacade fleetFacade;

    public OccupancyCommandServiceImpl(OccupancyWriter writer, FleetContextFacade fleetFacade) {
        this.writer = writer;
        this.fleetFacade = fleetFacade;
    }

    @Override
    public InitializeOccupancyResult initialize(InitializeOccupancyCommand command) {
        return retryOnce(() -> writer.initialize(command));
    }

    @Override
    public RecordHeartbeatResult heartbeat(RecordHeartbeatCommand command) {
        return retryOnce(() -> writer.heartbeat(command));
    }

    @Override
    public RecordOccupancyEventResult recordEvent(RecordOccupancyEventCommand command) {
        OccupancyEventWriteResult written = retryOnce(() -> writer.applyEvent(command));
        switch (written.outcome()) {
            case GAP:
                throw new SequenceGapException(written.expectedSequence());
            case NEGATIVE:
                throw new RuleViolationException("NEGATIVE_COUNT",
                    "the event would make the passenger count negative; the count must be re-initialized");
            default:
                Integer capacity = fleetFacade.findBusCapacity(command.busId())
                    .map(FleetContextFacade.BusCapacityInfo::capacity).orElse(null);
                return new RecordOccupancyEventResult(written.eventId(), written.sequence(), written.type(),
                    written.count(), OccupancyAssessment.loadOf(written.count(), capacity), written.duplicate());
        }
    }

    private <T> T retryOnce(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
            return action.get();  // retry once in a new transaction
        }
    }
}
