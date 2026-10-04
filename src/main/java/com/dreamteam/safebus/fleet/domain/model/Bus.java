package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.fleet.domain.port.QrCodeGenerator;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.Locale;

@Entity
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(unique = true, nullable = false, length = 15)
    private String plate;

    @Column(unique = true, nullable = false)
    private String qrCode;

    @Column(nullable = false)
    private boolean enabled;

    protected Bus() {}

    public static Bus create(Long companyId, String plate, QrCodeGenerator qrGen) {
        if (plate == null || plate.isBlank()) {
            throw new RuleViolationException("INVALID_PLATE", "plate is required");
        }
        String normalized = plate.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 15) {
            throw new RuleViolationException("INVALID_PLATE", "plate exceeds 15 characters");
        }
        Bus b = new Bus();
        b.companyId = companyId;
        b.plate = normalized;
        b.qrCode = qrGen.generate();
        b.enabled = true;
        return b;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getPlate() { return plate; }
    public String getQrCode() { return qrCode; }
    public boolean isEnabled() { return enabled; }
    public void disable() { this.enabled = false; }
}
