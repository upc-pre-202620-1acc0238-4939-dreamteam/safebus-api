package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetMyPassengerRequestQueryServiceImpl implements GetMyPassengerRequestQueryService {

    private final PassengerRequestRepository requestRepository;
    private final MyPassengerRequestViewBuilder viewBuilder;
    private final CurrentUserProvider currentUserProvider;

    public GetMyPassengerRequestQueryServiceImpl(PassengerRequestRepository requestRepository,
                                                 MyPassengerRequestViewBuilder viewBuilder,
                                                 CurrentUserProvider currentUserProvider) {
        this.requestRepository = requestRepository;
        this.viewBuilder = viewBuilder;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public MyPassengerRequestResult get(String requestId) {
        return viewBuilder.build(MyPassengerRequestAccess.ownedBy(requestRepository,
            currentUserProvider.current().userId(), requestId));
    }
}
