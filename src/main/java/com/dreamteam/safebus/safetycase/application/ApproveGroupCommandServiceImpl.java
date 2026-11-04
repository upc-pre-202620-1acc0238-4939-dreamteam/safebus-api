package com.dreamteam.safebus.safetycase.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

// Deliberately not @Transactional: the retry needs a new transaction after a constraint violation.
@Service
public class ApproveGroupCommandServiceImpl implements ApproveGroupCommandService {

    private final GroupDecisionWriter writer;
    private final SupervisorNotifier notifier;

    public ApproveGroupCommandServiceImpl(GroupDecisionWriter writer, SupervisorNotifier notifier) {
        this.writer = writer;
        this.notifier = notifier;
    }

    @Override
    public ApproveGroupResult approve(ApproveGroupCommand command) {
        ApproveGroupResult result;
        try {
            result = writer.approve(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            result = writer.approve(command); // retry once in a new transaction
        }
        if (!result.replay()) {
            notifier.groupApproved(result.groupId(), result.emergencyId());
        }
        return result;
    }
}
