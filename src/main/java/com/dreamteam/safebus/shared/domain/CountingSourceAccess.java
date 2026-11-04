package com.dreamteam.safebus.shared.domain;

import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;

// One denial for every way a device can be refused, so the caller cannot tell the reasons apart
public final class CountingSourceAccess {

    public static final String DENIED_CODE = "OCCUPANCY_SOURCE_NOT_AUTHORIZED";
    public static final String DENIED_MESSAGE = "access denied";

    private CountingSourceAccess() {}

    public static ForbiddenOperationException denied() {
        return new ForbiddenOperationException(DENIED_CODE, DENIED_MESSAGE);
    }
}
