package com.dreamteam.safebus.iam.interfaces.acl;

import com.dreamteam.safebus.iam.application.UserAccountCommandService;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import org.springframework.stereotype.Component;

@Component
public class IamContextFacade {

    private final UserAccountCommandService accountCommandService;

    public IamContextFacade(UserAccountCommandService accountCommandService) {
        this.accountCommandService = accountCommandService;
    }

    public Long createSupervisorAccount(String loginId, String rawPassword, Long companyId) {
        return accountCommandService.createSupervisor(loginId, rawPassword, companyId);
    }

    public Long createDriverAccount(String loginId, String rawPassword, Long companyId) {
        return accountCommandService.createDriver(loginId, rawPassword, companyId);
    }

    public Long createPassengerAccount(String loginId, String rawPassword) {
        return accountCommandService.createPassenger(loginId, rawPassword);
    }

    public void createSupervisorAccountIfAbsent(String loginId, String rawPassword, Long companyId) {
        try {
            accountCommandService.createSupervisor(loginId, rawPassword, companyId);
        } catch (ConflictException ignored) {
        }
    }

    public void createDriverAccountIfAbsent(String loginId, String rawPassword, Long companyId) {
        try {
            accountCommandService.createDriver(loginId, rawPassword, companyId);
        } catch (ConflictException ignored) {
        }
    }
}
