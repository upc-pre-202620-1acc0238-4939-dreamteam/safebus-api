package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.NotFoundException;
import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.CountingSource;
import com.dreamteam.safebus.trip.domain.model.CountingSourceStatus;
import com.dreamteam.safebus.trip.domain.port.CountingSourceKeys;
import com.dreamteam.safebus.trip.domain.repository.BusOccupancyRepository;
import com.dreamteam.safebus.trip.domain.repository.CountingSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CountingSourceWriterImpl implements CountingSourceWriter {

    private final CountingSourceRepository sourceRepository;
    private final BusOccupancyRepository occupancyRepository;
    private final CountingSourceKeys keys;
    private final FleetContextFacade fleetFacade;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public CountingSourceWriterImpl(CountingSourceRepository sourceRepository,
                                    BusOccupancyRepository occupancyRepository,
                                    CountingSourceKeys keys,
                                    FleetContextFacade fleetFacade,
                                    CurrentUserProvider currentUserProvider,
                                    Clock clock) {
        this.sourceRepository = sourceRepository;
        this.occupancyRepository = occupancyRepository;
        this.keys = keys;
        this.fleetFacade = fleetFacade;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public CountingSourceCredentialResult register(RegisterCountingSourceCommand command) {
        var user = currentUserProvider.current();
        Long companyId = requireOwnBus(command.busId(), user.companyId());
        if (sourceRepository.existsByBusIdAndStatus(command.busId(), CountingSourceStatus.ACTIVE)) {
            throw new ConflictException("ACTIVE_SOURCE_EXISTS", "the bus already has an active counting source");
        }
        CountingSourceKeys.GeneratedKey key = keys.generate();
        CountingSource source = sourceRepository.saveAndFlush(CountingSource.register(
            companyId, command.busId(), command.label(), key.hash(), user.userId(), clock));

        // Only a revoked source can precede this one, so an existing count belonged to it and is discarded
        var existing = occupancyRepository.findByBusIdForUpdate(command.busId());
        if (existing.isPresent()) {
            existing.get().resetForNewSource(source.getId());
            occupancyRepository.saveAndFlush(existing.get());
        } else {
            occupancyRepository.saveAndFlush(BusOccupancy.create(companyId, command.busId(), source.getId()));
        }
        return credential(source, key.rawKey());
    }

    @Override
    public CountingSourceCredentialResult rotate(RotateCountingSourceCommand command) {
        var user = currentUserProvider.current();
        requireOwnBus(command.busId(), user.companyId());
        CountingSource source = lockSourceOfBus(command.sourceId(), command.busId());
        CountingSourceKeys.GeneratedKey key = keys.generate();
        source.rotate(key.hash(), clock);
        sourceRepository.saveAndFlush(source);
        return credential(source, key.rawKey());
    }

    @Override
    public CountingSourceResult revoke(RevokeCountingSourceCommand command) {
        var user = currentUserProvider.current();
        requireOwnBus(command.busId(), user.companyId());
        CountingSource source = lockSourceOfBus(command.sourceId(), command.busId());
        if (source.revoke(clock)) {
            sourceRepository.saveAndFlush(source);
        }
        return CountingSourceResults.of(source);
    }

    private Long requireOwnBus(Long busId, Long supervisorCompanyId) {
        var busCompanyId = fleetFacade.findBusCompanyId(busId);
        // A foreign bus and a missing bus produce the same 403 so bus ids cannot be enumerated
        if (busCompanyId.isEmpty() || !busCompanyId.get().equals(supervisorCompanyId)) {
            throw new ForbiddenOperationException("BUS_ACCESS_DENIED", "access denied");
        }
        return busCompanyId.get();
    }

    private CountingSource lockSourceOfBus(Long sourceId, Long busId) {
        return sourceRepository.findByIdForUpdate(sourceId)
            .filter(s -> s.getBusId().equals(busId))
            .orElseThrow(() -> new NotFoundException("SOURCE_NOT_FOUND", "counting source not found"));
    }

    private static CountingSourceCredentialResult credential(CountingSource source, String rawKey) {
        return new CountingSourceCredentialResult(source.getId(), source.getBusId(), source.getLabel(),
            source.getStatus().name(), source.getCreatedAt(), source.getRotatedAt(), rawKey);
    }
}
