package com.dreamteam.safebus.iam.infrastructure;

import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.iam.interfaces.acl.IamContextFacade;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevSeeder {

    private final IamContextFacade iam;
    private final UserAccountRepository repository;

    public DevSeeder(IamContextFacade iam, UserAccountRepository repository) {
        this.iam = iam;
        this.repository = repository;
    }

    @PostConstruct
    public void seed() {
        if (!repository.existsByLoginId("SUP-001")) {
            iam.createSupervisorAccount("SUP-001", "Safebus2024!", 1L);
        }
        if (!repository.existsByLoginId("DRV-001")) {
            iam.createDriverAccount("DRV-001", "Safebus2024!", 1L);
        }
    }
}
