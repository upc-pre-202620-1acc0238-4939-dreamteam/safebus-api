package com.dreamteam.safebus.safetycase.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class CreateDriverEmergencyCommandServiceImpl implements CreateDriverEmergencyCommandService {

    private final EmergencyWriter writer;

    public CreateDriverEmergencyCommandServiceImpl(EmergencyWriter writer) {
        this.writer = writer;
    }

    @Override
    public CreateDriverEmergencyResult create(CreateDriverEmergencyCommand command) {
        try {
            return writer.write(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            return writer.write(command); // retry once in a new transaction
        }
    }
}
