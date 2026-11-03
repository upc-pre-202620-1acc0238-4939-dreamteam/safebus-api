package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.application.ImageStoragePort;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetRequestEvidenceQueryServiceImpl implements GetRequestEvidenceQueryService {

    private static final String ACCESS_DENIED = "evidence not found or not owned by this company";

    private final PassengerRequestRepository requestRepository;
    private final PassengerRequestGroupRepository groupRepository;
    private final ImageStoragePort imageStoragePort;
    private final CurrentUserProvider currentUserProvider;

    public GetRequestEvidenceQueryServiceImpl(PassengerRequestRepository requestRepository,
                                              PassengerRequestGroupRepository groupRepository,
                                              ImageStoragePort imageStoragePort,
                                              CurrentUserProvider currentUserProvider) {
        this.requestRepository = requestRepository;
        this.groupRepository = groupRepository;
        this.imageStoragePort = imageStoragePort;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public GetRequestEvidenceResult get(String requestId) {
        AuthenticatedUser caller = currentUserProvider.current();

        // Missing, malformed, late (no group) and foreign requests are indistinguishable
        PassengerRequest request = requestRepository.findById(requestId == null ? "" : requestId)
            .filter(r -> r.getGroupId() != null && r.getCompanyId().equals(caller.companyId()))
            .orElseThrow(() -> new ForbiddenOperationException("EVIDENCE_ACCESS_DENIED", ACCESS_DENIED));
        groupRepository.findById(request.getGroupId())
            .map(PassengerRequestGroup::getCompanyId)
            .filter(companyId -> companyId.equals(caller.companyId()))
            .orElseThrow(() -> new ForbiddenOperationException("EVIDENCE_ACCESS_DENIED", ACCESS_DENIED));

        return imageStoragePort.load(request.getEvidenceImageId())
            .map(image -> new GetRequestEvidenceResult(image.contentType(), image.content()))
            .orElseThrow(() -> new NotFoundException("EVIDENCE_NOT_FOUND", "the evidence photo is not available"));
    }
}
