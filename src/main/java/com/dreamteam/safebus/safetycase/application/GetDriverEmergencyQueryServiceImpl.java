package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import org.springframework.stereotype.Service;

@Service
public class GetDriverEmergencyQueryServiceImpl implements GetDriverEmergencyQueryService {

    private static final String ACCESS_DENIED = "emergency not found or not owned by this driver";

    private final EmergencyRepository emergencyRepository;
    private final FleetContextFacade fleetFacade;
    private final CurrentUserProvider currentUserProvider;

    public GetDriverEmergencyQueryServiceImpl(EmergencyRepository emergencyRepository,
                                               FleetContextFacade fleetFacade,
                                               CurrentUserProvider currentUserProvider) {
        this.emergencyRepository = emergencyRepository;
        this.fleetFacade = fleetFacade;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public GetDriverEmergencyResult getForDriver(String id) {
        Long callerUserId = currentUserProvider.current().userId();
        Long driverId = fleetFacade.findDriverByUserAccountId(callerUserId)
            .map(FleetContextFacade.DriverInfo::driverId)
            .orElseThrow(() -> new ForbiddenOperationException("EMERGENCY_ACCESS_DENIED", ACCESS_DENIED));

        var emergency = emergencyRepository.findById(id)
            .filter(e -> e.getDriverId() != null && e.getDriverId().equals(driverId))
            .orElseThrow(() -> new ForbiddenOperationException("EMERGENCY_ACCESS_DENIED", ACCESS_DENIED));

        return new GetDriverEmergencyResult(
            emergency.getId(), emergency.getStatus().name(),
            emergency.getActivatedAt(), emergency.getReceivedAt(),
            emergency.getAttentionStartedAt(), emergency.getClosedAt(),
            emergency.getUserResponse());
    }
}
