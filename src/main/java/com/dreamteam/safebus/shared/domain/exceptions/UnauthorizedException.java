package com.dreamteam.safebus.shared.domain.exceptions;

public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super("AUTHENTICATION_FAILED", message);
    }

    public UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
