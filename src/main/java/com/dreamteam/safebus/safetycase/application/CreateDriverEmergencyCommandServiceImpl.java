package com.dreamteam.safebus.safetycase.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class CreateDriverEmergencyCommandServiceImpl implements CreateDriverEmergencyCommandService {

    private final EmergencyWriter writer;
    private final SupervisorNotifier notifier;

    public CreateDriverEmergencyCommandServiceImpl(EmergencyWriter writer, SupervisorNotifier notifier) {
        this.writer = writer;
        this.notifier = notifier;
    }

    @Override
    public CreateDriverEmergencyResult create(CreateDriverEmergencyCommand command) {
        CreateDriverEmergencyResult result;
        try {
            result = writer.write(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            result = writer.write(command); // retry once in a new transaction
        }
        if (!result.duplicate()) {
            notifier.driverEmergencyCreated(result.id());
        }
        return result;
    }
}
