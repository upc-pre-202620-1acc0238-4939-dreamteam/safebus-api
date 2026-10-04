package com.dreamteam.safebus.iam.infrastructure;

import com.dreamteam.safebus.iam.interfaces.acl.IamContextFacade;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevSeeder {

    private final IamContextFacade iam;

    public DevSeeder(IamContextFacade iam) {
        this.iam = iam;
    }

    @PostConstruct
    public void seed() {
        iam.createSupervisorAccountIfAbsent("SUP-001", "Safebus2024!", 1L);
        iam.createDriverAccountIfAbsent("DRV-001", "Safebus2024!", 1L);
    }
}
