package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.passenger.interfaces.acl.PassengerContextFacade;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetBusSafetyHistoryQueryServiceImpl implements GetBusSafetyHistoryQueryService {
    private final PassengerContextFacade passengerFacade;
    private final EmergencyRepository emergencyRepository;
    private final CurrentUserProvider currentUserProvider;

    public GetBusSafetyHistoryQueryServiceImpl(PassengerContextFacade passengerFacade,
                                              EmergencyRepository emergencyRepository,
                                              CurrentUserProvider currentUserProvider) {
        this.passengerFacade = passengerFacade;
        this.emergencyRepository = emergencyRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public GetBusSafetyHistoryResult getForBus(Long busId) {
        var journey = passengerFacade.findActiveJourney(currentUserProvider.current().userId())
            .filter(active -> active.busId().equals(busId))
            .orElseThrow(() -> new ForbiddenOperationException("BUS_ACCESS_DENIED", "access denied"));
        var items = emergencyRepository.findByBusIdAndShiftIdOrderByReceivedAtDesc(busId, journey.shiftId())
            .stream()
            .map(emergency -> new GetBusSafetyHistoryResult.Item(
                PublicReference.of(emergency.getId()), emergency.getSource().name(),
                emergency.getReceivedAt(), emergency.getStatus().name()))
            .toList();
        return new GetBusSafetyHistoryResult(busId, journey.shiftId(), items,
            items.isEmpty() ? "No reports are recorded for this bus and shift." : null);
    }
}
