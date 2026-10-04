package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.fleet.domain.repository.ShiftAssignmentRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
// READ_COMMITTED ensures overlap queries see rows committed by concurrent transactions that hold the lock,
// preventing both requests from inserting when MySQL InnoDB's REPEATABLE_READ snapshot is stale.
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CreateShiftAssignmentCommandServiceImpl implements CreateShiftAssignmentCommandService {

    private static final String NOT_IN_COMPANY = "RESOURCE_NOT_IN_COMPANY";
    private static final String NOT_IN_COMPANY_MSG = "resource not found or not accessible";

    private final ShiftAssignmentRepository assignmentRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public CreateShiftAssignmentCommandServiceImpl(
            ShiftAssignmentRepository assignmentRepository,
            BusRepository busRepository,
            DriverRepository driverRepository,
            RouteRepository routeRepository,
            CurrentUserProvider currentUserProvider,
            Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.busRepository = busRepository;
        this.driverRepository = driverRepository;
        this.routeRepository = routeRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public ShiftAssignment create(CreateShiftAssignmentCommand cmd) {
        var user = currentUserProvider.current();

        // 1. Fail fast: validate period before touching the database
        ShiftAssignment.validatePeriod(cmd.plannedStart(), cmd.plannedEnd());

        // 2. Load each resource; same exception whether it is absent or foreign
        var bus = busRepository.findById(cmd.busId())
            .filter(b -> b.getCompanyId().equals(user.companyId()))
            .orElseThrow(() -> new RuleViolationException(NOT_IN_COMPANY, NOT_IN_COMPANY_MSG));
        if (!bus.isEnabled()) {
            throw new RuleViolationException("RESOURCE_DISABLED", "bus is disabled");
        }

        var driver = driverRepository.findById(cmd.driverId())
            .filter(d -> d.getCompanyId().equals(user.companyId()))
            .orElseThrow(() -> new RuleViolationException(NOT_IN_COMPANY, NOT_IN_COMPANY_MSG));
        if (!driver.isEnabled()) {
            throw new RuleViolationException("RESOURCE_DISABLED", "driver is disabled");
        }

        var route = routeRepository.findById(cmd.routeId())
            .filter(r -> r.getCompanyId().equals(user.companyId()))
            .orElseThrow(() -> new RuleViolationException(NOT_IN_COMPANY, NOT_IN_COMPANY_MSG));
        if (!route.isEnabled()) {
            throw new RuleViolationException("RESOURCE_DISABLED", "route is disabled");
        }

        // 3. Acquire pessimistic write locks — bus first, then driver (always this order)
        busRepository.findByIdForUpdate(bus.getId());
        driverRepository.findByIdForUpdate(driver.getId());

        // 4. Check for overlapping assignments (with locks held)
        boolean driverOverlap = !assignmentRepository.findOverlappingForDriver(
            driver.getId(), cmd.plannedStart(), cmd.plannedEnd()).isEmpty();
        boolean busOverlap = !assignmentRepository.findOverlappingForBus(
            bus.getId(), cmd.plannedStart(), cmd.plannedEnd()).isEmpty();

        if (driverOverlap || busOverlap) {
            String detail = driverOverlap && busOverlap
                ? "driver and bus both have overlapping assignments"
                : driverOverlap ? "driver has an overlapping assignment"
                : "bus has an overlapping assignment";
            throw new ConflictException("ASSIGNMENT_OVERLAP", detail);
        }

        // 5. Save
        return assignmentRepository.save(
            ShiftAssignment.create(
                driver.getId(), bus.getId(), route.getId(),
                cmd.plannedStart(), cmd.plannedEnd(),
                user.userId(), clock));
    }
}
