package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.port.CredentialGenerator;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.iam.interfaces.acl.IamContextFacade;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Service
@Transactional
public class CreateDriverCommandServiceImpl implements CreateDriverCommandService {

    private final DriverRepository driverRepository;
    private final IamContextFacade iamFacade;
    private final CredentialGenerator credentialGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final Duration qrCredentialTtl;

    public CreateDriverCommandServiceImpl(
            DriverRepository driverRepository,
            IamContextFacade iamFacade,
            CredentialGenerator credentialGenerator,
            CurrentUserProvider currentUserProvider,
            Clock clock,
            @Value("${safebus.driver.qr-credential-ttl:PT8760H}") Duration qrCredentialTtl) {
        this.driverRepository = driverRepository;
        this.iamFacade = iamFacade;
        this.credentialGenerator = credentialGenerator;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
        this.qrCredentialTtl = qrCredentialTtl;
    }

    @Override
    public Driver create(CreateDriverCommand command) {
        var user = currentUserProvider.current();
        Long userAccountId = iamFacade.createDriverAccount(
            command.loginId(), command.initialPassword(), user.companyId());
        Driver driver = Driver.create(
            user.companyId(), userAccountId, command.fullName(),
            credentialGenerator, qrCredentialTtl, clock);
        return driverRepository.save(driver);
    }
}
