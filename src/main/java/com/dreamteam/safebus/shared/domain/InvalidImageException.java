package com.dreamteam.safebus.shared.domain;

public class InvalidImageException extends RuntimeException {

    public enum Reason { TOO_LARGE, UNSUPPORTED_TYPE, UNDECODABLE, TOO_MANY_PIXELS }

    private final Reason reason;

    public InvalidImageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
