package com.dreamteam.safebus.trip.application;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

// Deliberately not transactional: marks written by the writer must survive the exceptions thrown here
@Service
public class OccupancyCommandServiceImpl implements OccupancyCommandService {

    private final OccupancyWriter writer;

    public OccupancyCommandServiceImpl(OccupancyWriter writer) {
        this.writer = writer;
    }

    @Override
    public InitializeOccupancyResult initialize(InitializeOccupancyCommand command) {
        return retryOnce(() -> writer.initialize(command));
    }

    @Override
    public RecordHeartbeatResult heartbeat(RecordHeartbeatCommand command) {
        return retryOnce(() -> writer.heartbeat(command));
    }

    private <T> T retryOnce(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
            return action.get();  // retry once in a new transaction
        }
    }
}
