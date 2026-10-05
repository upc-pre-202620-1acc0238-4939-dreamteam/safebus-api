package com.dreamteam.safebus.contact.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class RegisterContactRequest {

    private final ContactRequestWriter writer;

    public RegisterContactRequest(ContactRequestWriter writer) {
        this.writer = writer;
    }

    public RegisterContactRequestResult register(RegisterContactRequestCommand command) {
        try {
            return writer.write(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            return writer.write(command); // retry once in a new transaction
        }
    }
}
