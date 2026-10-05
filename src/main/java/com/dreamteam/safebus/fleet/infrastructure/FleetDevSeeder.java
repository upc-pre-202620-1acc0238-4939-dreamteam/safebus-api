package com.dreamteam.safebus.fleet.infrastructure;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import com.dreamteam.safebus.fleet.domain.port.QrCodeGenerator;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.fleet.domain.repository.ShiftAssignmentRepository;
import com.dreamteam.safebus.iam.interfaces.acl.IamContextFacade;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@Profile("dev")
public class FleetDevSeeder implements ApplicationRunner {

    private final CompanyRepository companyRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final IamContextFacade iam;
    private final QrCodeGenerator qrCodeGenerator;
    private final Clock clock;

    public FleetDevSeeder(CompanyRepository companyRepository,
                          BusRepository busRepository,
                          DriverRepository driverRepository,
                          RouteRepository routeRepository,
                          ShiftAssignmentRepository assignmentRepository,
                          IamContextFacade iam,
                          QrCodeGenerator qrCodeGenerator,
                          Clock clock) {
        this.companyRepository = companyRepository;
        this.busRepository = busRepository;
        this.driverRepository = driverRepository;
        this.routeRepository = routeRepository;
        this.assignmentRepository = assignmentRepository;
        this.iam = iam;
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

        Long supervisorAccountId = iam.createSupervisorAccount("sup-001", "Safebus2024!", company.getId());

        Long driverAccountId = iam.createDriverAccount("drv-001", "Safebus2024!", company.getId());
        Driver driver = driverRepository.save(Driver.create(
            company.getId(), driverAccountId, "Demo Driver",
            () -> "DEV-DRIVER-QR-001", Duration.ofDays(365), clock));

        Bus bus = busRepository.save(Bus.create(company.getId(), "ABC-123", qrCodeGenerator));

        Route route = routeRepository.save(
            Route.create(company.getId(), "Main Route", "Terminal Norte", "Terminal Sur"));

        Instant now = Instant.now(clock);
        assignmentRepository.save(ShiftAssignment.create(
            driver.getId(), bus.getId(), route.getId(),
            now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(8)),
            supervisorAccountId, clock));
    }
}
