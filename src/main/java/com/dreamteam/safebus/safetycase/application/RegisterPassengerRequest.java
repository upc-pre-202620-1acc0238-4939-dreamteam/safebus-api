package com.dreamteam.safebus.safetycase.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

// Deliberately not @Transactional: the retry needs a new transaction after a constraint violation.
@Service
public class RegisterPassengerRequest {

    private final PassengerRequestWriter writer;
    private final SupervisorNotifier notifier;

    public RegisterPassengerRequest(PassengerRequestWriter writer, SupervisorNotifier notifier) {
        this.writer = writer;
        this.notifier = notifier;
    }

    public RegisterPassengerRequestResult register(RegisterPassengerRequestCommand command) {
        RegisterPassengerRequestResult result;
        try {
            result = writer.write(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            result = writer.write(command); // retry once in a new transaction
        }
        if (!result.duplicate() && result.reviewRequired()) {
            notifier.groupReviewRequired(result.groupId());
        }
        return result;
    }
}
