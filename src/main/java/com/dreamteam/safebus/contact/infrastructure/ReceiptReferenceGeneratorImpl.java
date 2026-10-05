package com.dreamteam.safebus.contact.infrastructure;

import com.dreamteam.safebus.contact.domain.port.ReceiptReferenceGenerator;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class ReceiptReferenceGeneratorImpl implements ReceiptReferenceGenerator {

    @Override
    public String generate() {
        return "CR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }
}
