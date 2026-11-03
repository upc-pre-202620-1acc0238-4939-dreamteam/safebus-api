package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.application.ImageStoragePort;
import com.dreamteam.safebus.shared.domain.exceptions.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetMyRequestEvidenceQueryServiceImpl implements GetMyRequestEvidenceQueryService {

    private final PassengerRequestRepository requestRepository;
    private final ImageStoragePort imageStoragePort;
    private final CurrentUserProvider currentUserProvider;

    public GetMyRequestEvidenceQueryServiceImpl(PassengerRequestRepository requestRepository,
                                                ImageStoragePort imageStoragePort,
                                                CurrentUserProvider currentUserProvider) {
        this.requestRepository = requestRepository;
        this.imageStoragePort = imageStoragePort;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public GetRequestEvidenceResult get(String requestId) {
        PassengerRequest request = MyPassengerRequestAccess.ownedBy(requestRepository,
            currentUserProvider.current().userId(), requestId);

        // Only reachable once ownership is proven, so it reveals nothing about other accounts' ids
        return imageStoragePort.load(request.getEvidenceImageId())
            .map(image -> new GetRequestEvidenceResult(image.contentType(), image.content()))
            .orElseThrow(() -> new NotFoundException("EVIDENCE_NOT_FOUND", "the evidence photo is not available"));
    }
}
