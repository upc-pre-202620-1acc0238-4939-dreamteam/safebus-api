package com.dreamteam.safebus.safetycase.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

// Deliberately not @Transactional: the retry needs a new transaction after a constraint violation.
@Service
public class DeclineGroupCommandServiceImpl implements DeclineGroupCommandService {

    private final GroupDecisionWriter writer;

    public DeclineGroupCommandServiceImpl(GroupDecisionWriter writer) {
        this.writer = writer;
    }

    @Override
    public DeclineGroupResult decline(DeclineGroupCommand command) {
        try {
            return writer.decline(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            return writer.decline(command); // retry once in a new transaction
        }
    }
}
