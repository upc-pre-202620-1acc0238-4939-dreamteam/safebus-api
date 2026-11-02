package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ExpireOverdueGroups {

    private final PassengerRequestGroupRepository groupRepository;
    private final Clock clock;

    public ExpireOverdueGroups(PassengerRequestGroupRepository groupRepository, Clock clock) {
        this.groupRepository = groupRepository;
        this.clock = clock;
    }

    public int expire() {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        List<PassengerRequestGroup> overdue = groupRepository.findOverdueCollectingForUpdate(now);
        for (PassengerRequestGroup group : overdue) {
            if (group.isWindowOverdue(now)) {
                group.expire();
            }
        }
        groupRepository.saveAllAndFlush(overdue);
        return overdue.size();
    }
}
