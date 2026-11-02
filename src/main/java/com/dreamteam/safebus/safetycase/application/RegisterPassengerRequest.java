package com.dreamteam.safebus.safetycase.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

// Deliberately not @Transactional: the retry needs a new transaction after a constraint violation.
@Service
public class RegisterPassengerRequest {

    private final PassengerRequestWriter writer;

    public RegisterPassengerRequest(PassengerRequestWriter writer) {
        this.writer = writer;
    }

    public RegisterPassengerRequestResult register(RegisterPassengerRequestCommand command) {
        try {
            return writer.write(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            return writer.write(command); // retry once in a new transaction
        }
    }
}
