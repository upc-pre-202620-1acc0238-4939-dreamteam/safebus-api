package com.dreamteam.safebus.safetycase.infrastructure.scheduling;

import com.dreamteam.safebus.safetycase.application.ExpireOverdueGroups;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GroupExpiryJobTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(ExpireOverdueGroups.class, () -> mock(ExpireOverdueGroups.class))
        .withUserConfiguration(GroupExpiryJob.class);

    @Test
    void runDelegatesToTheExpiryUseCase() {
        ExpireOverdueGroups useCase = mock(ExpireOverdueGroups.class);

        new GroupExpiryJob(useCase).run();

        verify(useCase).expire();
    }

    @Test
    void theJobIsRegisteredWhenTheFlagIsTrueOrMissing() {
        runner.withPropertyValues("safebus.scheduling.enabled=true")
            .run(ctx -> assertEquals(1, ctx.getBeansOfType(GroupExpiryJob.class).size()));
        runner.run(ctx -> assertEquals(1, ctx.getBeansOfType(GroupExpiryJob.class).size()));
    }

    @Test
    void theJobIsAbsentWhenTheFlagIsFalse() {
        runner.withPropertyValues("safebus.scheduling.enabled=false")
            .run(ctx -> assertTrue(ctx.getBeansOfType(GroupExpiryJob.class).isEmpty()));
    }
}
