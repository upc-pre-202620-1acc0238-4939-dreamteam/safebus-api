package com.dreamteam.safebus.fleet.infrastructure;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.port.CredentialGenerator;
import com.dreamteam.safebus.fleet.domain.port.QrCodeGenerator;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.iam.interfaces.acl.IamContextFacade;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Component
@Profile("dev")
public class FleetDevSeeder implements ApplicationRunner {

    private final CompanyRepository companyRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final IamContextFacade iam;
    private final CredentialGenerator credentialGenerator;
    private final QrCodeGenerator qrCodeGenerator;
    private final Clock clock;

    public FleetDevSeeder(CompanyRepository companyRepository,
                          BusRepository busRepository,
                          DriverRepository driverRepository,
                          RouteRepository routeRepository,
                          IamContextFacade iam,
                          CredentialGenerator credentialGenerator,
                          QrCodeGenerator qrCodeGenerator,
                          Clock clock) {
        this.companyRepository = companyRepository;
        this.busRepository = busRepository;
        this.driverRepository = driverRepository;
        this.routeRepository = routeRepository;
        this.iam = iam;
        this.credentialGenerator = credentialGenerator;
        this.qrCodeGenerator = qrCodeGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (companyRepository.count() > 0) {
            return;
        }

        Company company = companyRepository.save(Company.create("Demo Transport"));

        iam.createSupervisorAccount("sup-001", "Safebus2024!", company.getId());

        Long driverAccountId = iam.createDriverAccount("drv-001", "Safebus2024!", company.getId());
        driverRepository.save(Driver.create(
            company.getId(), driverAccountId, "Demo Driver",
            credentialGenerator, Duration.ofDays(365), clock));

        busRepository.save(Bus.create(company.getId(), "ABC-123", qrCodeGenerator));

        routeRepository.save(Route.create(company.getId(), "Main Route", "Terminal Norte", "Terminal Sur"));
    }
}
