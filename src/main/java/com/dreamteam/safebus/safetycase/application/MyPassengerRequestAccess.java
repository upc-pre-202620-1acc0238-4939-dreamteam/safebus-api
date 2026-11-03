package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;

final class MyPassengerRequestAccess {

    static final String CODE = "PASSENGER_REQUEST_ACCESS_DENIED";
    static final String MESSAGE = "request not found or not owned by this account";

    private MyPassengerRequestAccess() {}

    // Another account's, a missing and a malformed id are indistinguishable.
    static PassengerRequest ownedBy(PassengerRequestRepository repository, Long callerUserId, String rawId) {
        String id;
        try {
            id = PassengerRequest.normalizeId(rawId);
        } catch (RuleViolationException ex) {
            throw denied();
        }
        return repository.findById(id)
            .filter(r -> r.getPassengerUserId().equals(callerUserId))
            .orElseThrow(MyPassengerRequestAccess::denied);
    }

    private static ForbiddenOperationException denied() {
        return new ForbiddenOperationException(CODE, MESSAGE);
    }
}
