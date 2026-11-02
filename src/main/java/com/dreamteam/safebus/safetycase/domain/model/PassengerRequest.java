package com.dreamteam.safebus.safetycase.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "passenger_user_id"}))
public class PassengerRequest implements Persistable<String> {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Id
    private String id;

    @Transient
    private boolean isNew = true;

    @PostLoad
    void markNotNew() { this.isNew = false; }

    @Override
    public String getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    // null while the request contributes to no group (LATE)
    @Column
    private Long groupId;

    @Column(nullable = false)
    private Long passengerUserId;

    @Column(nullable = false)
    private Long journeyId;

    @Column(nullable = false)
    private Long busId;

    @Column(nullable = false)
    private Long shiftId;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, length = PassengerRequestPolicy.MAX_MESSAGE_CODE_POINTS)
    private String message;

    @Column(nullable = false)
    private Long evidenceImageId;

    @Column(nullable = false, length = 64)
    private String evidenceSha256;

    @Column(nullable = false)
    private Instant activatedAt;

    @Column(nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    protected PassengerRequest() {}

    public static String normalizeId(String rawId) {
        if (rawId == null || !CANONICAL_UUID.matcher(rawId).matches()) {
            throw new RuleViolationException("INVALID_REQUEST_ID", "id must be a UUID in text form");
        }
        return UUID.fromString(rawId).toString();
    }

    public static String normalizeMessage(String rawMessage) {
        String trimmed = rawMessage == null ? "" : rawMessage.trim();
        if (trimmed.isEmpty()) {
            throw new RuleViolationException("MESSAGE_REQUIRED", "message is required");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > PassengerRequestPolicy.MAX_MESSAGE_CODE_POINTS) {
            throw new RuleViolationException("MESSAGE_TOO_LONG",
                "message must not exceed " + PassengerRequestPolicy.MAX_MESSAGE_CODE_POINTS + " characters");
        }
        return trimmed;
    }

    public static PassengerRequest create(String id, Long groupId, Long passengerUserId, Long journeyId,
                                          Long busId, Long shiftId, Long companyId, String message,
                                          Long evidenceImageId, String evidenceSha256, Instant activatedAt,
                                          Instant now, RequestStatus status) {
        if ((status == RequestStatus.LATE) != (groupId == null)) {
            throw new IllegalArgumentException("only a LATE request has no group");
        }
        PassengerRequest r = new PassengerRequest();
        r.id = normalizeId(id);
        r.groupId = groupId;
        r.passengerUserId = passengerUserId;
        r.journeyId = journeyId;
        r.busId = busId;
        r.shiftId = shiftId;
        r.companyId = companyId;
        r.message = normalizeMessage(message);
        r.evidenceImageId = evidenceImageId;
        r.evidenceSha256 = evidenceSha256;
        r.activatedAt = activatedAt.truncatedTo(ChronoUnit.MILLIS);
        r.receivedAt = now.truncatedTo(ChronoUnit.MILLIS);
        r.status = status;
        return r;
    }

    public boolean hasSamePayloadAs(Long passengerUserId, Long journeyId, String message,
                                    Instant activatedAt, String evidenceSha256) {
        return this.passengerUserId.equals(passengerUserId)
            && this.journeyId.equals(journeyId)
            && this.message.equals(message.trim())
            && this.activatedAt.equals(activatedAt.truncatedTo(ChronoUnit.MILLIS))
            && this.evidenceSha256.equals(evidenceSha256);
    }

    public Long getGroupId() { return groupId; }
    public Long getPassengerUserId() { return passengerUserId; }
    public Long getJourneyId() { return journeyId; }
    public Long getBusId() { return busId; }
    public Long getShiftId() { return shiftId; }
    public Long getCompanyId() { return companyId; }
    public String getMessage() { return message; }
    public Long getEvidenceImageId() { return evidenceImageId; }
    public String getEvidenceSha256() { return evidenceSha256; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public RequestStatus getStatus() { return status; }
}
