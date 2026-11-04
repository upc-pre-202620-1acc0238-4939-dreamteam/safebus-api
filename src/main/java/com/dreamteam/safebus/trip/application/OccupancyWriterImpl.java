package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.shared.application.CountingSourcePrincipal;
import com.dreamteam.safebus.shared.application.CurrentCountingSourceProvider;
import com.dreamteam.safebus.shared.domain.CountingSourceAccess;
import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.CountingSource;
import com.dreamteam.safebus.trip.domain.model.InitializationOutcome;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.repository.BusOccupancyRepository;
import com.dreamteam.safebus.trip.domain.repository.CountingSourceRepository;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
// READ_COMMITTED prevents MVCC snapshot gap when acquiring row locks inside this writer
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OccupancyWriterImpl implements OccupancyWriter {

    private final BusOccupancyRepository occupancyRepository;
    private final CountingSourceRepository sourceRepository;
    private final DriverShiftRepository shiftRepository;
    private final CurrentCountingSourceProvider currentSource;
    private final Clock clock;

    public OccupancyWriterImpl(BusOccupancyRepository occupancyRepository,
                               CountingSourceRepository sourceRepository,
                               DriverShiftRepository shiftRepository,
                               CurrentCountingSourceProvider currentSource,
                               Clock clock) {
        this.occupancyRepository = occupancyRepository;
        this.sourceRepository = sourceRepository;
        this.shiftRepository = shiftRepository;
        this.currentSource = currentSource;
        this.clock = clock;
    }

    @Override
    public InitializeOccupancyResult initialize(InitializeOccupancyCommand command) {
        Instant now = Instant.now(clock);
        BusOccupancy.requireCaptureTimeNotInTheFuture(command.confirmedAt(), now);
        requireOwnActiveSource(command.busId());
        requireActiveShift(command.busId(), command.shiftId());
        BusOccupancy occupancy = lockOccupancy(command.busId());

        InitializationOutcome outcome = occupancy.initialize(command.initializationId(), command.shiftId(),
            command.confirmedCount(), command.confirmedAt(), now);
        if (outcome == InitializationOutcome.APPLIED) {
            occupancyRepository.saveAndFlush(occupancy);
        }
        return new InitializeOccupancyResult(command.initializationId(), command.busId(), command.shiftId(),
            command.confirmedCount(), command.confirmedAt().truncatedTo(ChronoUnit.MILLIS),
            outcome == InitializationOutcome.DUPLICATE);
    }

    @Override
    public RecordHeartbeatResult heartbeat(RecordHeartbeatCommand command) {
        Instant now = Instant.now(clock);
        requireOwnActiveSource(command.busId());
        BusOccupancy occupancy = lockOccupancy(command.busId());
        occupancy.recordHeartbeat(now);
        occupancyRepository.saveAndFlush(occupancy);
        return new RecordHeartbeatResult(command.busId(), occupancy.getLastHeartbeatAt());
    }

    // Every refusal is the same denial, so the caller cannot tell a wrong key from another bus
    private CountingSourcePrincipal requireOwnActiveSource(Long busId) {
        CountingSourcePrincipal principal = currentSource.current();
        if (!principal.busId().equals(busId)) {
            throw CountingSourceAccess.denied();
        }
        sourceRepository.findById(principal.sourceId())
            .filter(CountingSource::isActive)
            .orElseThrow(CountingSourceAccess::denied);
        return principal;
    }

    private void requireActiveShift(Long busId, Long shiftId) {
        boolean active = shiftRepository.findTopByBusIdAndStatusOrderByStartedAtDesc(busId, ShiftStatus.ACTIVE)
            .map(s -> s.getId().equals(shiftId))
            .orElse(false);
        if (!active) {
            throw CountingSourceAccess.denied();
        }
    }

    private BusOccupancy lockOccupancy(Long busId) {
        return occupancyRepository.findByBusIdForUpdate(busId).orElseThrow(CountingSourceAccess::denied);
    }
}
