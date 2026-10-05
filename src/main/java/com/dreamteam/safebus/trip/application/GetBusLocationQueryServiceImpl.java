package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.NotFoundException;
import com.dreamteam.safebus.trip.domain.repository.VehicleLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetBusLocationQueryServiceImpl implements GetBusLocationQueryService {

    private final VehicleLocationRepository vehicleLocationRepository;
    private final FleetContextFacade fleetFacade;
    private final PassengerJourneyAccessPort passengerJourneyAccessPort;
    private final CurrentUserProvider currentUserProvider;

    public GetBusLocationQueryServiceImpl(VehicleLocationRepository vehicleLocationRepository,
                                           FleetContextFacade fleetFacade,
                                           PassengerJourneyAccessPort passengerJourneyAccessPort,
                                           CurrentUserProvider currentUserProvider) {
        this.vehicleLocationRepository = vehicleLocationRepository;
        this.fleetFacade = fleetFacade;
        this.passengerJourneyAccessPort = passengerJourneyAccessPort;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public BusLocationResult getLocation(Long busId) {
        var user = currentUserProvider.current();

        if ("SUPERVISOR".equals(user.role())) {
            // A foreign bus and a non-existent bus produce the same 403 so bus ids cannot be enumerated
            var busCompanyId = fleetFacade.findBusCompanyId(busId);
            if (busCompanyId.isEmpty() || !busCompanyId.get().equals(user.companyId())) {
                throw new ForbiddenOperationException("BUS_ACCESS_DENIED", "access denied");
            }
        } else if ("PASSENGER".equals(user.role())) {
            if (!passengerJourneyAccessPort.hasActiveJourneyOnBus(user.userId(), busId)) {
                throw new ForbiddenOperationException("BUS_ACCESS_DENIED", "access denied");
            }
        } else {
            throw new ForbiddenOperationException("BUS_ACCESS_DENIED", "access denied");
        }

        var vl = vehicleLocationRepository.findByBusId(busId)
            .orElseThrow(() -> new NotFoundException("LOCATION_UNAVAILABLE",
                "no location available for this bus"));

        return new BusLocationResult(
            busId,
            vl.getPoint().getLatitude(),
            vl.getPoint().getLongitude(),
            vl.getCapturedAt(),
            vl.getAccuracyMeters());
    }
}
