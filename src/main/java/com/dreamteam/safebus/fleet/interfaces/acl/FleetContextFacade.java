package com.dreamteam.safebus.fleet.interfaces.acl;

import com.dreamteam.safebus.fleet.domain.model.AssignmentStatus;
import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.ShiftAssignmentRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.NotFoundException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
public class FleetContextFacade {

    public record DriverInfo(Long driverId, Long userAccountId, Long companyId,
                             boolean enabled, Instant credentialExpiresAt) {}

    public record AssignmentActivationResult(Long assignmentId, Long driverId,
                                             Long busId, Long routeId) {}

    private final DriverRepository driverRepository;
    private final ShiftAssignmentRepository assignmentRepository;

    public FleetContextFacade(DriverRepository driverRepository,
                               ShiftAssignmentRepository assignmentRepository) {
        this.driverRepository = driverRepository;
        this.assignmentRepository = assignmentRepository;
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
            .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND", "assignment not found"));
        try {
            sa.activate();
        } catch (RuleViolationException e) {
            if ("ASSIGNMENT_NOT_AVAILABLE".equals(e.code())) {
                throw new ConflictException("SHIFT_ALREADY_ACTIVE",
                    "assignment is not available for activation");
            }
            throw e;
        }
        assignmentRepository.save(sa);
        return new AssignmentActivationResult(sa.getId(), sa.getDriverId(),
                                              sa.getBusId(), sa.getRouteId());
    }
}
