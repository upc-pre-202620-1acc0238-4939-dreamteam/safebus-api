package com.dreamteam.safebus.passenger.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_account_id", "active_marker"}))
public class PassengerJourney {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userAccountId;

    @Column(nullable = false)
    private Long busId;

    @Column(nullable = false)
    private Long shiftId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JourneyStatus status;

    @Column(nullable = false)
    private Instant startedAt;

    @Column
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column
    private JourneyEndReason endReason;

    // 1 while ACTIVE, null once ENDED; the unique constraint allows multiple NULLs in MySQL/H2
    @Column(name = "active_marker")
    private Integer activeMarker;

    protected PassengerJourney() {}

    public static PassengerJourney start(Long userAccountId, Long busId, Long shiftId, Clock clock) {
        PassengerJourney j = new PassengerJourney();
        j.userAccountId = userAccountId;
        j.busId = busId;
        j.shiftId = shiftId;
        j.status = JourneyStatus.ACTIVE;
        j.startedAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        j.activeMarker = 1;
        return j;
    }

    public boolean end(JourneyEndReason reason, Instant now) {
        if (status == JourneyStatus.ENDED) {
            return false;
        }
        this.status = JourneyStatus.ENDED;
        this.endedAt = now.truncatedTo(ChronoUnit.MILLIS);
        this.endReason = reason;
        this.activeMarker = null;
        return true;
    }

    public Long getId() { return id; }
    public Long getUserAccountId() { return userAccountId; }
    public Long getBusId() { return busId; }
    public Long getShiftId() { return shiftId; }
    public JourneyStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public JourneyEndReason getEndReason() { return endReason; }
    public Integer getActiveMarker() { return activeMarker; }
}
