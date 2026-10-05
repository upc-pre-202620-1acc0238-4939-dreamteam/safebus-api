package com.dreamteam.safebus.trip.interfaces.acl;

import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.dreamteam.safebus.trip.domain.repository.VehicleLocationRepository;
import org.springframework.stereotype.Component;


import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@Component
public class TripContextFacade {

    public record ShiftInfo(Long shiftId, Long driverId, Long busId, Long routeId, String status) {}

    public record ActiveShiftView(Long shiftId, Long busId, Long driverId, Long routeId,
                                  Instant startedAt) {}

    public record BusPositionView(Long busId, double latitude, double longitude,
                                  double accuracyMeters, Instant capturedAt) {}

    private final DriverShiftRepository driverShiftRepository;
    private final VehicleLocationRepository vehicleLocationRepository;

    public TripContextFacade(DriverShiftRepository driverShiftRepository,
                             VehicleLocationRepository vehicleLocationRepository) {
        this.driverShiftRepository = driverShiftRepository;
        this.vehicleLocationRepository = vehicleLocationRepository;
    }

    public Optional<ShiftInfo> findShiftById(Long shiftId) {
        return driverShiftRepository.findById(shiftId)
            .map(s -> new ShiftInfo(s.getId(), s.getDriverId(), s.getBusId(),
                                    s.getRouteId(), s.getStatus().name()));
    }

    // returns the latest ACTIVE shift for the bus; a CLOSED shift is never returned
    public Optional<ShiftInfo> findActiveShiftByBusId(Long busId) {
        return driverShiftRepository.findTopByBusIdAndStatusOrderByStartedAtDesc(busId, ShiftStatus.ACTIVE)
            .map(s -> new ShiftInfo(s.getId(), s.getDriverId(), s.getBusId(),
                                    s.getRouteId(), s.getStatus().name()));
    }

    // Latest ACTIVE shift per bus by startedAt; when startedAt is identical the greatest shift id wins
    public Map<Long, ActiveShiftView> findActiveShiftsByBusIds(Collection<Long> busIds) {
        if (busIds == null || busIds.isEmpty()) {
            return Map.of();
        }
        BinaryOperator<ActiveShiftView> latest = BinaryOperator.maxBy(
            Comparator.comparing(ActiveShiftView::startedAt).thenComparing(ActiveShiftView::shiftId));
        return driverShiftRepository.findByBusIdInAndStatus(busIds, ShiftStatus.ACTIVE).stream()
            .map(s -> new ActiveShiftView(s.getId(), s.getBusId(), s.getDriverId(),
                                          s.getRouteId(), s.getStartedAt()))
            .collect(Collectors.toUnmodifiableMap(ActiveShiftView::busId, v -> v, latest));
    }

    // A VehicleLocation row that has no point yet is treated as absent
    public Map<Long, BusPositionView> findLastPositionsByBusIds(Collection<Long> busIds) {
        if (busIds == null || busIds.isEmpty()) {
            return Map.of();
        }
        return vehicleLocationRepository.findByBusIdIn(busIds).stream()
            .filter(vl -> vl.getPoint() != null && vl.getCapturedAt() != null)
            .map(vl -> new BusPositionView(vl.getBusId(), vl.getPoint().getLatitude(),
                                           vl.getPoint().getLongitude(), vl.getAccuracyMeters(),
                                           vl.getCapturedAt()))
            .collect(Collectors.toUnmodifiableMap(BusPositionView::busId, v -> v));
    }
}
