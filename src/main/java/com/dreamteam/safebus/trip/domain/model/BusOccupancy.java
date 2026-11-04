package com.dreamteam.safebus.trip.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.trip.domain.exceptions.SequenceGapException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

// The passenger count of one bus, driven by strictly sequential events from its counting source
@Entity
public class BusOccupancy {

    public static final Duration MAX_CAPTURE_SKEW = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bus_id", nullable = false, unique = true)
    private Long busId;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long sourceId;

    @Column
    private Long shiftId;

    // bumped on every initialization so sequences restart without colliding with earlier events
    @Column(nullable = false)
    private int epoch;

    @Column(length = 36)
    private String initializationId;

    @Column(name = "event_count")
    private Integer count;

    @Column(nullable = false)
    private long lastSequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsistencyState consistency;

    // while in GAP: the highest sequence seen so far; reaching it restores consistency
    @Column
    private Long recoverAtSequence;

    @Column
    private Instant lastCountAt;

    @Column
    private Instant lastHeartbeatAt;

    @Version
    private Long version;

    protected BusOccupancy() {}

    public static BusOccupancy create(Long companyId, Long busId, Long sourceId) {
        BusOccupancy o = new BusOccupancy();
        o.companyId = companyId;
        o.busId = busId;
        o.sourceId = sourceId;
        o.consistency = ConsistencyState.NOT_INITIALIZED;
        o.lastSequence = 0;
        return o;
    }

    // A new source after a revocation starts from scratch; the bumped epoch keeps old events apart
    public void resetForNewSource(Long sourceId) {
        this.sourceId = sourceId;
        this.shiftId = null;
        this.epoch++;
        this.initializationId = null;
        this.count = null;
        this.lastSequence = 0;
        this.consistency = ConsistencyState.NOT_INITIALIZED;
        this.recoverAtSequence = null;
        this.lastCountAt = null;
        this.lastHeartbeatAt = null;
    }

    public static void requireCaptureTimeNotInTheFuture(Instant capturedAt, Instant now) {
        if (capturedAt.isAfter(now.plus(MAX_CAPTURE_SKEW))) {
            throw new RuleViolationException("INVALID_CAPTURE_TIME",
                "capture time is too far in the future");
        }
    }

    public InitializationOutcome initialize(String initializationId, Long shiftId, int confirmedCount,
                                            Instant confirmedAt, Instant now) {
        if (confirmedCount != 0) {
            throw new RuleViolationException("INVALID_INITIAL_COUNT", "the confirmed initial count must be 0");
        }
        requireCaptureTimeNotInTheFuture(confirmedAt, now);
        if (initializationId.equals(this.initializationId) && shiftId.equals(this.shiftId)) {
            return InitializationOutcome.DUPLICATE;
        }
        boolean allowed = consistency == ConsistencyState.NOT_INITIALIZED
            || consistency == ConsistencyState.INCONSISTENT
            || !shiftId.equals(this.shiftId);
        if (!allowed) {
            throw new ConflictException("ALREADY_INITIALIZED", "the occupancy is already initialized");
        }
        this.initializationId = initializationId;
        this.shiftId = shiftId;
        this.epoch++;
        this.lastSequence = 0;
        this.count = 0;
        this.consistency = ConsistencyState.CONSISTENT;
        this.recoverAtSequence = null;
        this.lastCountAt = confirmedAt.truncatedTo(ChronoUnit.MILLIS);
        return InitializationOutcome.APPLIED;
    }

    // The caller has already settled duplicates; GAP and NEGATIVE are marks the caller must commit before answering
    public EventOutcome apply(Long eventShiftId, OccupancyEventType type, long sequence, Instant capturedAt) {
        if (consistency == ConsistencyState.NOT_INITIALIZED || !eventShiftId.equals(shiftId)) {
            throw new RuleViolationException("OCCUPANCY_NOT_INITIALIZED",
                "the occupancy has not been initialized for this shift");
        }
        if (consistency == ConsistencyState.INCONSISTENT) {
            throw new RuleViolationException("OCCUPANCY_INCONSISTENT",
                "the count is inconsistent and must be re-initialized");
        }
        if (sequence <= lastSequence) {
            throw new ConflictException("SEQUENCE_ALREADY_APPLIED", "this sequence was already applied");
        }
        if (sequence > lastSequence + 1) {
            this.consistency = ConsistencyState.GAP;
            this.recoverAtSequence = Math.max(recoverAtSequence == null ? 0 : recoverAtSequence, sequence);
            return EventOutcome.GAP;
        }
        if (type == OccupancyEventType.EXIT && count == 0) {
            this.consistency = ConsistencyState.INCONSISTENT;
            this.recoverAtSequence = null;
            return EventOutcome.NEGATIVE;
        }
        this.count = type == OccupancyEventType.ENTRY ? count + 1 : count - 1;
        this.lastSequence = sequence;
        this.lastCountAt = capturedAt.truncatedTo(ChronoUnit.MILLIS);
        if (consistency == ConsistencyState.GAP && sequence >= recoverAtSequence) {
            this.consistency = ConsistencyState.CONSISTENT;
            this.recoverAtSequence = null;
        }
        return EventOutcome.APPLIED;
    }

    public SequenceGapException gap() {
        return new SequenceGapException(lastSequence + 1);
    }

    public void recordHeartbeat(Instant now) {
        this.lastHeartbeatAt = now.truncatedTo(ChronoUnit.MILLIS);
    }

    public Long getId() { return id; }
    public Long getBusId() { return busId; }
    public Long getCompanyId() { return companyId; }
    public Long getSourceId() { return sourceId; }
    public Long getShiftId() { return shiftId; }
    public int getEpoch() { return epoch; }
    public String getInitializationId() { return initializationId; }
    public Integer getCount() { return count; }
    public long getLastSequence() { return lastSequence; }
    public ConsistencyState getConsistency() { return consistency; }
    public Long getRecoverAtSequence() { return recoverAtSequence; }
    public Instant getLastCountAt() { return lastCountAt; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public Long getVersion() { return version; }
}
