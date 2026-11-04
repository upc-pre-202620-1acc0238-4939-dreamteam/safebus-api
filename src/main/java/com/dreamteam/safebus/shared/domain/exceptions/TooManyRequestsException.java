package com.dreamteam.safebus.shared.domain.exceptions;

public class TooManyRequestsException extends DomainException {

    public TooManyRequestsException(String code, String message) {
        super(code, message);
    }
}
