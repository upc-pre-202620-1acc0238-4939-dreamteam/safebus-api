package com.dreamteam.safebus.safetycase.infrastructure.scheduling;

import com.dreamteam.safebus.safetycase.application.ExpireOverdueGroups;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "safebus.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class GroupExpiryJob {

    private final ExpireOverdueGroups expireOverdueGroups;

    public GroupExpiryJob(ExpireOverdueGroups expireOverdueGroups) {
        this.expireOverdueGroups = expireOverdueGroups;
    }

    @Scheduled(fixedDelay = 30_000)
    public void run() {
        expireOverdueGroups.expire();
    }
}
