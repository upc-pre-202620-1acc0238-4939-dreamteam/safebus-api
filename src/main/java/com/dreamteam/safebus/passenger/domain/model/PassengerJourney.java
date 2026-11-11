package com.dreamteam.safebus.passenger.domain.model;

import com.dreamteam.safebus.shared.domain.TimePolicy;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
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
import java.time.Duration;
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

    @Column
    private Integer separationDistanceMeters;

    @Column
    private Integer separationDurationSeconds;

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
        return end(reason, null, null, now);
    }

    // A replay on an ended journey changes nothing; the first end wins.
    public boolean end(JourneyEndReason reason, Instant reportedEndedAt, SeparationSummary summary, Instant now) {
        if (status == JourneyStatus.ENDED) {
            return false;
        }
        Instant resolved = resolveEndedAt(reportedEndedAt, now.truncatedTo(ChronoUnit.MILLIS));
        if (summary != null) {
            if (reason != JourneyEndReason.AUTOMATIC_SEPARATION) {
                throw new RuleViolationException("INVALID_END_SUMMARY",
                    "a separation summary is only valid with reason AUTOMATIC_SEPARATION");
            }
            if (summary.durationSeconds() > Duration.between(startedAt, resolved).getSeconds()) {
                throw new RuleViolationException("INVALID_END_SUMMARY",
                    "separation duration must not exceed the length of the journey");
            }
            this.separationDistanceMeters = summary.distanceMeters();
            this.separationDurationSeconds = summary.durationSeconds();
        }
        this.status = JourneyStatus.ENDED;
        this.endedAt = resolved;
        this.endReason = reason;
        this.activeMarker = null;
        return true;
    }

    private Instant resolveEndedAt(Instant reportedEndedAt, Instant now) {
        if (reportedEndedAt == null) {
            return now;
        }
        Instant reported = reportedEndedAt.truncatedTo(ChronoUnit.MILLIS);
        if (reported.isBefore(startedAt)) {
            throw new RuleViolationException("INVALID_END_TIME", "endedAt must not be before the journey start");
        }
        if (reported.isAfter(now.plus(TimePolicy.MAX_FUTURE_SKEW))) {
            throw new RuleViolationException("INVALID_END_TIME", "endedAt must not be more than 5 minutes in the future");
        }
        return reported.isAfter(now) ? now : reported;
    }

    public SeparationSummary getSeparationSummary() {
        return separationDistanceMeters == null ? null
            : new SeparationSummary(separationDistanceMeters, separationDurationSeconds);
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
