package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetMyPassengerRequestsQueryServiceImpl implements GetMyPassengerRequestsQueryService {

    static final String EMPTY_MESSAGE = "No requests are recorded for this account.";

    private final PassengerRequestRepository requestRepository;
    private final MyPassengerRequestViewBuilder viewBuilder;
    private final CurrentUserProvider currentUserProvider;

    public GetMyPassengerRequestsQueryServiceImpl(PassengerRequestRepository requestRepository,
                                                  MyPassengerRequestViewBuilder viewBuilder,
                                                  CurrentUserProvider currentUserProvider) {
        this.requestRepository = requestRepository;
        this.viewBuilder = viewBuilder;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public GetMyPassengerRequestsResult list() {
        Long caller = currentUserProvider.current().userId();
        List<MyPassengerRequestResult> items =
            requestRepository.findByPassengerUserIdOrderByReceivedAtDescIdDesc(caller).stream()
                .map(viewBuilder::build).toList();
        return new GetMyPassengerRequestsResult(items, items.isEmpty() ? EMPTY_MESSAGE : null);
    }
}
