package com.dreamteam.safebus.shared.domain.exceptions;

public class ForbiddenOperationException extends DomainException {

    public ForbiddenOperationException(String message) {
        super("FORBIDDEN_OPERATION", message);
    }

    public ForbiddenOperationException(String code, String message) {
        super(code, message);
    }
}
