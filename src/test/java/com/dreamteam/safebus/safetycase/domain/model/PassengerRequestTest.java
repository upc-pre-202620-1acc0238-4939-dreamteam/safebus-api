package com.dreamteam.safebus.safetycase.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassengerRequestTest {

    private static final Instant NOW = Instant.parse("2030-05-01T10:00:00.987654Z");
    private static final Instant ACTIVATED = Instant.parse("2030-05-01T09:58:00.555999Z");
    private static final String SHA = "a".repeat(64);

    private PassengerRequest request(String id, Long groupId, String message, RequestStatus status) {
        return PassengerRequest.create(id, groupId, 10L, 20L, 30L, 40L, 50L, message, 60L, SHA,
            ACTIVATED, NOW, status);
    }

    private PassengerRequest counted() {
        return request(UUID.randomUUID().toString(), 7L, "  bus in danger  ", RequestStatus.COUNTED);
    }

    private String codeOf(Runnable action) {
        return assertThrows(RuleViolationException.class, action::run).code();
    }

    @Test
    void create_keepsEveryFieldAndTruncatesBothTimestampsToMilliseconds() {
        String id = UUID.randomUUID().toString();

        PassengerRequest r = request(id, 7L, "bus in danger", RequestStatus.COUNTED);

        assertEquals(id, r.getId());
        assertEquals(7L, r.getGroupId());
        assertEquals(10L, r.getPassengerUserId());
        assertEquals(20L, r.getJourneyId());
        assertEquals(30L, r.getBusId());
        assertEquals(40L, r.getShiftId());
        assertEquals(50L, r.getCompanyId());
        assertEquals(60L, r.getEvidenceImageId());
        assertEquals(SHA, r.getEvidenceSha256());
        assertEquals(RequestStatus.COUNTED, r.getStatus());
        assertEquals(Instant.parse("2030-05-01T09:58:00.555Z"), r.getActivatedAt());
        assertEquals(Instant.parse("2030-05-01T10:00:00.987Z"), r.getReceivedAt());
    }

    @Test
    void create_storesTheMessageTrimmed() {
        assertEquals("bus in danger", counted().getMessage());
    }

    @Test
    void create_aLateRequestHasNoGroupAndAGroupedRequestMustHaveOne() {
        PassengerRequest late = request(UUID.randomUUID().toString(), null, "m", RequestStatus.LATE);
        assertNull(late.getGroupId());

        assertThrows(IllegalArgumentException.class,
            () -> request(UUID.randomUUID().toString(), null, "m", RequestStatus.COUNTED));
        assertThrows(IllegalArgumentException.class,
            () -> request(UUID.randomUUID().toString(), 7L, "m", RequestStatus.LATE));
        assertEquals(RequestStatus.ATTACHED,
            request(UUID.randomUUID().toString(), 7L, "m", RequestStatus.ATTACHED).getStatus());
    }

    @Test
    void normalizeMessage_rejectsBlankAndOverlongTextAndCountsCodePointsAfterTrimming() {
        assertEquals("MESSAGE_REQUIRED", codeOf(() -> PassengerRequest.normalizeMessage(null)));
        assertEquals("MESSAGE_REQUIRED", codeOf(() -> PassengerRequest.normalizeMessage("")));
        assertEquals("MESSAGE_REQUIRED", codeOf(() -> PassengerRequest.normalizeMessage(" \t\n ")));
        assertEquals("MESSAGE_TOO_LONG", codeOf(() -> PassengerRequest.normalizeMessage("x".repeat(501))));

        assertEquals(500, PassengerRequest.normalizeMessage("x".repeat(500)).length());
        assertEquals(500, PassengerRequest.normalizeMessage("  " + "x".repeat(500) + "  ").length());

        String emoji = "🚀";
        assertEquals(1000, PassengerRequest.normalizeMessage(emoji.repeat(500)).length());
        assertEquals("MESSAGE_TOO_LONG", codeOf(() -> PassengerRequest.normalizeMessage(emoji.repeat(501))));
    }

    @Test
    void normalizeId_acceptsCanonicalUuidsAndStoresThemInLowercase() {
        String upper = "123E4567-E89B-12D3-A456-426614174000";

        assertEquals(upper.toLowerCase(), PassengerRequest.normalizeId(upper));
        assertEquals(upper.toLowerCase(), request(upper, 7L, "m", RequestStatus.COUNTED).getId());
    }

    @Test
    void normalizeId_rejectsNonCanonicalStringsThatUuidFromStringWouldAccept() {
        assertEquals(UUID.fromString("1-1-1-1-1").toString(), "00000001-0001-0001-0001-000000000001");

        for (String bad : new String[] {"1-1-1-1-1", null, "", " ", "not-a-uuid",
                "123e4567-e89b-12d3-a456-42661417400", "123e4567-e89b-12d3-a456-4266141740000",
                " 123e4567-e89b-12d3-a456-426614174000", "123e4567e89b12d3a456426614174000",
                "123e4567-e89b-12d3-a456-42661417400g"}) {
            assertEquals("INVALID_REQUEST_ID", codeOf(() -> PassengerRequest.normalizeId(bad)), "id: " + bad);
        }
    }

    @Test
    void hasSamePayloadAs_comparesAccountJourneyTrimmedMessageMillisecondTimeAndEvidenceHash() {
        PassengerRequest r = counted();

        assertTrue(r.hasSamePayloadAs(10L, 20L, "bus in danger", ACTIVATED, SHA));
        assertTrue(r.hasSamePayloadAs(10L, 20L, "\tbus in danger ", Instant.parse("2030-05-01T09:58:00.555Z"), SHA));
        assertFalse(r.hasSamePayloadAs(11L, 20L, "bus in danger", ACTIVATED, SHA));
        assertFalse(r.hasSamePayloadAs(10L, 21L, "bus in danger", ACTIVATED, SHA));
        assertFalse(r.hasSamePayloadAs(10L, 20L, "other", ACTIVATED, SHA));
        assertFalse(r.hasSamePayloadAs(10L, 20L, "bus in danger", ACTIVATED.plusMillis(1), SHA));
        assertFalse(r.hasSamePayloadAs(10L, 20L, "bus in danger", ACTIVATED, "b".repeat(64)));
    }

    @Test
    void persistable_aFreshRequestIsNew() {
        assertTrue(counted().isNew());
    }
}
