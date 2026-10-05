package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.fleet.domain.port.QrCodeGenerator;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Column
    private Integer capacity;

    @Column(length = 100)
    private String capacityReference;

    @Column
    private Long capacityUpdatedByUserId;

    @Column
    private Instant capacityUpdatedAt;

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

    public void recordCapacity(Number capacity, String reference, Long authorUserId, Clock clock) {
        if (reference == null || reference.isBlank()) {
            throw new RuleViolationException("CAPACITY_REFERENCE_REQUIRED", "technicalRecordReference is required");
        }
        String trimmedReference = reference.trim();
        if (trimmedReference.length() > 100) {
            throw new RuleViolationException("CAPACITY_REFERENCE_TOO_LONG", "technicalRecordReference must not exceed 100 characters");
        }
        if (capacity == null) {
            throw new RuleViolationException("CAPACITY_REQUIRED", "capacity is required");
        }
        int recordedCapacity;
        try {
            recordedCapacity = new BigDecimal(capacity.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new RuleViolationException("INVALID_CAPACITY", "capacity must be an integer from 1 to 2147483647");
        }
        if (recordedCapacity <= 0) {
            throw new RuleViolationException("INVALID_CAPACITY", "capacity must be an integer from 1 to 2147483647");
        }
        Instant updatedAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        this.capacity = recordedCapacity;
        this.capacityReference = trimmedReference;
        this.capacityUpdatedByUserId = authorUserId;
        this.capacityUpdatedAt = updatedAt;
    }

    public Integer getCapacity() { return capacity; }
    public String getCapacityReference() { return capacityReference; }
    public Long getCapacityUpdatedByUserId() { return capacityUpdatedByUserId; }
    public Instant getCapacityUpdatedAt() { return capacityUpdatedAt; }
    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getPlate() { return plate; }
    public String getQrCode() { return qrCode; }
    public boolean isEnabled() { return enabled; }
    public void disable() { this.enabled = false; }
}
