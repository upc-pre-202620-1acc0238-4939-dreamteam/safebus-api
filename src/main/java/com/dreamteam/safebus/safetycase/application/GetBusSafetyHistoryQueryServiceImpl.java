package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.passenger.interfaces.acl.PassengerContextFacade;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencySource;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetBusSafetyHistoryQueryServiceImpl implements GetBusSafetyHistoryQueryService {

    static final String EMPTY_MESSAGE = "No reports are recorded for this bus and shift.";
    private static final String GROUP_SOURCE = "PASSENGER_GROUP";

    private final PassengerContextFacade passengerFacade;
    private final EmergencyRepository emergencyRepository;
    private final PassengerRequestGroupRepository groupRepository;
    private final PassengerRequestRepository requestRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public GetBusSafetyHistoryQueryServiceImpl(PassengerContextFacade passengerFacade,
                                              EmergencyRepository emergencyRepository,
                                              PassengerRequestGroupRepository groupRepository,
                                              PassengerRequestRepository requestRepository,
                                              CurrentUserProvider currentUserProvider, Clock clock) {
        this.passengerFacade = passengerFacade;
        this.emergencyRepository = emergencyRepository;
        this.groupRepository = groupRepository;
        this.requestRepository = requestRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public GetBusSafetyHistoryResult getForBus(String rawBusId, String rawShiftId) {
        Long busId = parse(rawBusId);
        Long requestedShiftId = rawShiftId == null ? null : parse(rawShiftId);
        var journey = passengerFacade.findActiveJourney(currentUserProvider.current().userId())
            .filter(active -> active.busId().equals(busId))
            .filter(active -> requestedShiftId == null || active.shiftId().equals(requestedShiftId))
            .orElseThrow(GetBusSafetyHistoryQueryServiceImpl::denied);
        Long shiftId = journey.shiftId();

        List<Emergency> emergencies = emergencyRepository.findByBusIdAndShiftIdOrderByReceivedAtDesc(busId, shiftId);
        Map<Long, PassengerRequestGroup> groups = new HashMap<>();
        groupRepository.findByBusIdAndShiftIdOrderByIdAsc(busId, shiftId).forEach(g -> groups.put(g.getId(), g));
        Instant now = Instant.now(clock);

        List<GetBusSafetyHistoryResult.Item> items = new ArrayList<>();
        Map<Long, Emergency> emergencyOfGroup = new HashMap<>();
        for (Emergency emergency : emergencies) {
            PassengerRequestGroup group = emergency.getSource() == EmergencySource.PASSENGER_GROUP
                ? groups.get(emergency.getGroupId()) : null;
            if (group == null) {
                items.add(driverItem(emergency));
            } else {
                emergencyOfGroup.put(group.getId(), emergency);
                items.add(groupItem(group, Optional.of(emergency), emergency.getReceivedAt(), now));
            }
        }
        groups.values().stream().filter(g -> !emergencyOfGroup.containsKey(g.getId()))
            .forEach(g -> items.add(groupItem(g, Optional.empty(), g.getWindowStartedAt(), now)));

        items.sort(Comparator.comparing(GetBusSafetyHistoryResult.Item::recordedAt).reversed()
            .thenComparing(GetBusSafetyHistoryResult.Item::reference));
        return new GetBusSafetyHistoryResult(busId, shiftId, items, items.isEmpty() ? EMPTY_MESSAGE : null);
    }

    private GetBusSafetyHistoryResult.Item driverItem(Emergency emergency) {
        return new GetBusSafetyHistoryResult.Item(PublicReference.of(emergency.getId()),
            emergency.getSource().name(), emergency.getReceivedAt(), emergency.getStatus().name(),
            PassengerRequestProgress.valueOf(emergency.getStatus().name()).label(), null);
    }

    private GetBusSafetyHistoryResult.Item groupItem(PassengerRequestGroup group, Optional<Emergency> emergency,
                                                     Instant recordedAt, Instant now) {
        PassengerRequestProgress progress = GroupProgress.of(group, emergency, now);
        int requests = (int) requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.COUNTED);
        return new GetBusSafetyHistoryResult.Item(
            PublicReference.ofGroup(group.getId(), group.getPublicReference()), GROUP_SOURCE, recordedAt,
            progress.name(), progress.label(), requests);
    }

    private static Long parse(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw denied();
        }
    }

    private static ForbiddenOperationException denied() {
        return new ForbiddenOperationException("BUS_ACCESS_DENIED", "access denied");
    }
}
