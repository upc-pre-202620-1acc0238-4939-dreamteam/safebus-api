package com.dreamteam.safebus.fleet.infrastructure;

import com.dreamteam.safebus.fleet.domain.port.QrCodeGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class QrCodeGeneratorImpl implements QrCodeGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
