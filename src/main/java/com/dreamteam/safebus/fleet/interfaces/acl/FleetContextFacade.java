package com.dreamteam.safebus.fleet.interfaces.acl;

import com.dreamteam.safebus.fleet.domain.model.AssignmentStatus;
import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.fleet.domain.repository.ShiftAssignmentRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class FleetContextFacade {

    public record DriverInfo(Long driverId, Long userAccountId, Long companyId,
                             boolean enabled, Instant credentialExpiresAt) {}

    public record AssignmentActivationResult(Long assignmentId, Long driverId,
                                             Long busId, Long routeId) {}

    public record CurrentAssignmentView(Long assignmentId, String status,
                                        String busPlate, String routeName,
                                        String origin, String destination,
                                        Instant plannedStart, Instant plannedEnd) {}

    public record BusInfo(Long busId, Long companyId, boolean enabled) {}

    public record ServiceInfo(String plate, String companyName, boolean companyValidated,
                              String routeName, String origin, String destination,
                              String driverPublicName) {}

    private final DriverRepository driverRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final CompanyRepository companyRepository;

    public FleetContextFacade(DriverRepository driverRepository,
                               ShiftAssignmentRepository assignmentRepository,
                               BusRepository busRepository,
                               RouteRepository routeRepository,
                               CompanyRepository companyRepository) {
        this.driverRepository = driverRepository;
        this.assignmentRepository = assignmentRepository;
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
        this.companyRepository = companyRepository;
    }

    public Optional<DriverInfo> findDriverByQrCredential(String qrCredential) {
        return driverRepository.findByQrCredential(qrCredential)
            .map(d -> new DriverInfo(d.getId(), d.getUserAccountId(), d.getCompanyId(),
                                     d.isEnabled(), d.getQrCredentialExpiresAt()));
    }

    // Row lock must be held within the caller's transaction; MANDATORY enforces that.
    @Transactional(propagation = Propagation.MANDATORY)
    public AssignmentActivationResult activateAssignment(Long assignmentId, Long driverId) {
        ShiftAssignment sa = assignmentRepository.findByIdForUpdate(assignmentId)
            .filter(a -> a.getDriverId().equals(driverId))
            .orElseThrow(() -> new RuleViolationException("ASSIGNMENT_NOT_FOUND", "assignment not found"));
        if (sa.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new ConflictException("ASSIGNMENT_NOT_AVAILABLE", "assignment is not in ASSIGNED status");
        }
        sa.activate();
        assignmentRepository.save(sa);
        return new AssignmentActivationResult(sa.getId(), sa.getDriverId(),
                                              sa.getBusId(), sa.getRouteId());
    }

    public Optional<DriverInfo> findDriverByUserAccountId(Long userAccountId) {
        return driverRepository.findByUserAccountId(userAccountId)
            .map(d -> new DriverInfo(d.getId(), d.getUserAccountId(), d.getCompanyId(),
                                     d.isEnabled(), d.getQrCredentialExpiresAt()));
    }

    public Optional<Long> findBusCompanyId(Long busId) {
        return busRepository.findById(busId)
            .map(Bus::getCompanyId);
    }

    public Optional<BusInfo> findBusByQrCode(String qrCode) {
        return busRepository.findByQrCode(qrCode)
            .map(b -> new BusInfo(b.getId(), b.getCompanyId(), b.isEnabled()));
    }

    public Optional<ServiceInfo> describeService(Long busId, Long routeId, Long driverId) {
        var bus     = busRepository.findById(busId).orElse(null);
        var route   = routeRepository.findById(routeId).orElse(null);
        var driver  = driverRepository.findById(driverId).orElse(null);
        if (bus == null || route == null || driver == null) return Optional.empty();
        var company = companyRepository.findById(bus.getCompanyId()).orElse(null);
        if (company == null) return Optional.empty();
        return Optional.of(new ServiceInfo(
            bus.getPlate(), company.getName(), company.isValidated(),
            route.getName(), route.getOrigin(), route.getDestination(),
            driver.getFullName()));
    }

    public Optional<CurrentAssignmentView> findCurrentAssignmentForUserAccount(Long userAccountId,
                                                                                Instant now) {
        return driverRepository.findByUserAccountId(userAccountId)
            .flatMap(driver -> {
                List<ShiftAssignment> candidates =
                    assignmentRepository.findCurrentCandidatesForDriver(driver.getId(), now);
                return candidates.stream()
                    .min(Comparator
                        .comparingInt((ShiftAssignment sa) ->
                            sa.getStatus() == AssignmentStatus.ACTIVE ? 0 : 1)
                        .thenComparing(ShiftAssignment::getPlannedStart));
            })
            .map(sa -> {
                var bus = busRepository.findById(sa.getBusId()).orElseThrow();
                var route = routeRepository.findById(sa.getRouteId()).orElseThrow();
                return new CurrentAssignmentView(
                    sa.getId(), sa.getStatus().name(),
                    bus.getPlate(), route.getName(),
                    route.getOrigin(), route.getDestination(),
                    sa.getPlannedStart(), sa.getPlannedEnd());
            });
    }
}
