package com.dreamteam.safebus.shared.domain.exceptions;

public class ForbiddenOperationException extends DomainException {

    public ForbiddenOperationException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "FORBIDDEN_OPERATION";
    }
}
