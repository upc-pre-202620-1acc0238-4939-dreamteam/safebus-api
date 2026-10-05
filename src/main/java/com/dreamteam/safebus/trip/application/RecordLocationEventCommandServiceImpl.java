package com.dreamteam.safebus.trip.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class RecordLocationEventCommandServiceImpl implements RecordLocationEventCommandService {

    private final LocationEventWriter writer;

    public RecordLocationEventCommandServiceImpl(LocationEventWriter writer) {
        this.writer = writer;
    }

    @Override
    public RecordLocationEventResult record(RecordLocationEventCommand command) {
        try {
            return writer.write(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            return writer.write(command);  // retry once in a new transaction
        }
    }
}
