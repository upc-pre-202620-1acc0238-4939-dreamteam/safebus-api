package com.dreamteam.safebus.safetycase.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

// Deliberately not @Transactional: the retry needs a new transaction after a constraint violation.
@Service
public class ApproveGroupCommandServiceImpl implements ApproveGroupCommandService {

    private final GroupDecisionWriter writer;

    public ApproveGroupCommandServiceImpl(GroupDecisionWriter writer) {
        this.writer = writer;
    }

    @Override
    public ApproveGroupResult approve(ApproveGroupCommand command) {
        try {
            return writer.approve(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            return writer.approve(command); // retry once in a new transaction
        }
    }
}
