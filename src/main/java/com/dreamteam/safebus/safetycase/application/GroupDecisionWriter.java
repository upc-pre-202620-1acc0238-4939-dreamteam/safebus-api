package com.dreamteam.safebus.safetycase.application;

public interface GroupDecisionWriter {
    ApproveGroupResult approve(ApproveGroupCommand command);

    DeclineGroupResult decline(DeclineGroupCommand command);
}
