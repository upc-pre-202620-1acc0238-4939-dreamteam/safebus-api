package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
// READ_COMMITTED prevents stale MySQL snapshots after acquiring the bus row lock.
@Transactional(isolation = Isolation.READ_COMMITTED)
public class UpdateBusCapacity {

    private final BusRepository busRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public UpdateBusCapacity(BusRepository busRepository, CurrentUserProvider currentUserProvider, Clock clock) {
        this.busRepository = busRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    public Bus update(UpdateBusCapacityCommand command) {
        Bus bus = busRepository.findByIdForUpdate(command.busId())
            .orElseThrow(() -> new ForbiddenOperationException("BUS_ACCESS_DENIED", "bus is not accessible"));
        var user = currentUserProvider.current();
        if (!bus.getCompanyId().equals(user.companyId())) {
            throw new ForbiddenOperationException("BUS_ACCESS_DENIED", "bus is not accessible");
        }
        bus.recordCapacity(command.capacity(), command.technicalRecordReference(), user.userId(), clock);
        return busRepository.save(bus);
    }
}
