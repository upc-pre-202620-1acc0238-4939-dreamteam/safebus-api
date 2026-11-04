package com.dreamteam.safebus.trip.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.domain.Persistable;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

// An applied count event; rejected events are never stored
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"bus_id", "epoch", "sequence_number"}))
public class OccupancyEvent implements Persistable<String> {

    @Id
    @Column(length = 36)
    private String id;

    @Transient
    private boolean isNew = true;

    @PostLoad
    void markNotNew() { this.isNew = false; }

    @Override
    public String getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @Column(name = "bus_id", nullable = false)
    private Long busId;

    @Column(nullable = false)
    private Long shiftId;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private int epoch;

    @Column(name = "sequence_number", nullable = false)
    private long sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OccupancyEventType type;

    @Column(nullable = false)
    private Instant capturedAt;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private int resultingCount;

    protected OccupancyEvent() {}

    public static OccupancyEvent applied(String eventId, BusOccupancy occupancy, OccupancyEventType type,
                                         long sequence, Instant capturedAt, Clock clock) {
        OccupancyEvent e = new OccupancyEvent();
        e.id = eventId;
        e.busId = occupancy.getBusId();
        e.shiftId = occupancy.getShiftId();
        e.sourceId = occupancy.getSourceId();
        e.epoch = occupancy.getEpoch();
        e.sequence = sequence;
        e.type = type;
        e.capturedAt = capturedAt.truncatedTo(ChronoUnit.MILLIS);
        e.receivedAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        e.resultingCount = occupancy.getCount();
        return e;
    }

    public boolean hasSamePayload(Long busId, Long shiftId, OccupancyEventType type,
                                  long sequence, Instant capturedAt) {
        return this.busId.equals(busId)
            && this.shiftId.equals(shiftId)
            && this.type == type
            && this.sequence == sequence
            && this.capturedAt.equals(capturedAt.truncatedTo(ChronoUnit.MILLIS));
    }

    public Long getBusId() { return busId; }
    public Long getShiftId() { return shiftId; }
    public Long getSourceId() { return sourceId; }
    public int getEpoch() { return epoch; }
    public long getSequence() { return sequence; }
    public OccupancyEventType getType() { return type; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public int getResultingCount() { return resultingCount; }
}
