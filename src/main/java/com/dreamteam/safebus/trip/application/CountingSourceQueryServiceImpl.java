package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.trip.domain.repository.CountingSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CountingSourceQueryServiceImpl implements CountingSourceQueryService {

    private final CountingSourceRepository sourceRepository;
    private final FleetContextFacade fleetFacade;
    private final CurrentUserProvider currentUserProvider;

    public CountingSourceQueryServiceImpl(CountingSourceRepository sourceRepository,
                                          FleetContextFacade fleetFacade,
                                          CurrentUserProvider currentUserProvider) {
        this.sourceRepository = sourceRepository;
        this.fleetFacade = fleetFacade;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public List<CountingSourceResult> listByBus(GetCountingSourcesByBusQuery query) {
        Long companyId = currentUserProvider.current().companyId();
        var busCompanyId = fleetFacade.findBusCompanyId(query.busId());
        if (busCompanyId.isEmpty() || !busCompanyId.get().equals(companyId)) {
            throw new ForbiddenOperationException("BUS_ACCESS_DENIED", "access denied");
        }
        return sourceRepository.findByBusIdOrderByIdAsc(query.busId()).stream()
            .map(CountingSourceResults::of)
            .toList();
    }
}
