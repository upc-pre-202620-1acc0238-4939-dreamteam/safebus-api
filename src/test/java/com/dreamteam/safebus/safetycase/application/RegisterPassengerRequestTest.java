package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.safetycase.support.TestImages;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.DomainException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class RegisterPassengerRequestTest extends AbstractPassengerRequestTest {

    private static final long U1 = 61001L;
    private static final long U2 = 61002L;
    private static final long U3 = 61003L;
    private static final long U4 = 61004L;
    private static final byte[] PHOTO = TestImages.jpeg(1);
    private static final int FIVE_MIB = 5 * 1024 * 1024;

    @Autowired RegisterPassengerRequest service;
    @MockitoBean CurrentUserProvider currentUser;

    private void as(long userId) {
        when(currentUser.current()).thenReturn(new AuthenticatedUser(userId, "PASSENGER", null));
    }

    private RegisterPassengerRequestCommand command(PassengerJourney journey, String id, String message,
                                                    Instant activatedAt, byte[] photo) {
        return new RegisterPassengerRequestCommand(id, journey.getId(), message, activatedAt, photo);
    }

    private RegisterPassengerRequestCommand valid(PassengerJourney journey) {
        return command(journey, UUID.randomUUID().toString(), "Danger on board",
            clock.instant().minusSeconds(30), PHOTO);
    }

    private RegisterPassengerRequestResult submit(long userId, PassengerJourney journey) {
        as(userId);
        return service.register(valid(journey));
    }

    private record State(long requests, long groups, long images) {}

    private State state() {
        return new State(requestRepository.count(), groupsOfShift().size(), storedImagesAdded());
    }

    private <T extends DomainException> T assertRejected(Class<T> type, String code,
                                                         RegisterPassengerRequestCommand command) {
        State before = state();
        T ex = assertThrows(type, () -> service.register(command));
        assertEquals(code, ex.code());
        assertEquals(before, state(), "a rejection must leave no request, group or image");
        return ex;
    }

    // --- US08 S1 / S2 ---

    @Test
    void firstCompleteRequest_isCountedInANewCollectingGroupWithEveryFieldRecorded() {
        PassengerJourney journey = activeJourney(U1);
        Instant activatedAt = clock.instant().minusSeconds(45).plusMillis(321);
        as(U1);

        RegisterPassengerRequestResult result = service.register(
            command(journey, UUID.randomUUID().toString(), "  Danger on board  ", activatedAt, PHOTO));

        assertEquals("COUNTED", result.status());
        assertEquals("COLLECTING", result.groupStatus());
        assertEquals(1, result.contributorCount());
        assertFalse(result.duplicate());
        assertEquals(clock.instant(), result.receivedAt());

        PassengerRequest stored = requestRepository.findById(result.requestId()).orElseThrow();
        assertEquals(U1, stored.getPassengerUserId());
        assertEquals(journey.getId(), stored.getJourneyId());
        assertEquals(bus.getId(), stored.getBusId());
        assertEquals(shift.getId(), stored.getShiftId());
        assertEquals(company.getId(), stored.getCompanyId());
        assertEquals("Danger on board", stored.getMessage());
        assertEquals(activatedAt, stored.getActivatedAt());
        assertEquals(clock.instant(), stored.getReceivedAt());
        assertEquals(RequestStatus.COUNTED, stored.getStatus());
        assertArrayEquals(PHOTO, storedImageBytes(stored.getEvidenceImageId()));

        PassengerRequestGroup group = groupsOfShift().getFirst();
        assertEquals(group.getId(), stored.getGroupId());
        assertEquals(company.getId(), group.getCompanyId());
        assertEquals(bus.getId(), group.getBusId());
        assertEquals(clock.instant(), group.getWindowStartedAt());
        assertEquals(clock.instant().plus(Duration.ofMinutes(5)), group.getWindowExpiresAt());
        assertEquals(GroupStatus.COLLECTING, group.getStatus());
        assertEquals(0, emergencyRepository.count());
    }

    @Test
    void evidenceSha256IsStoredAsTheHexDigestOfTheImageBytes() throws Exception {
        RegisterPassengerRequestResult result = submit(U1, activeJourney(U1));

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PHOTO));
        assertEquals(expected, requestRepository.findById(result.requestId()).orElseThrow().getEvidenceSha256());
    }

    @Test
    void thirdDistinctAccountMovesTheGroupToAwaitingApprovalAndCreatesNoEmergency() {
        PassengerJourney j1 = activeJourney(U1);
        PassengerJourney j2 = activeJourney(U2);
        PassengerJourney j3 = activeJourney(U3);

        RegisterPassengerRequestResult first = submit(U1, j1);
        RegisterPassengerRequestResult second = submit(U2, j2);
        assertEquals("COLLECTING", second.groupStatus());
        assertEquals(2, second.contributorCount());
        RegisterPassengerRequestResult third = submit(U3, j3);

        assertEquals("COUNTED", third.status());
        assertEquals("AWAITING_APPROVAL", third.groupStatus());
        assertEquals(3, third.contributorCount());
        assertEquals(1, groupsOfShift().size());
        PassengerRequestGroup group = groupsOfShift().getFirst();
        assertEquals(GroupStatus.AWAITING_APPROVAL, group.getStatus());
        assertEquals(clock.instant(), group.getAwaitingSince());
        assertEquals(1, group.getOpenMarker());
        assertEquals(first.requestId(), requestRepository.findById(first.requestId()).orElseThrow().getId());
        assertEquals(0, emergencyRepository.count());
    }

    @Test
    void thirdRequestOneMillisecondBeforeTheWindowExpiresStillReachesTheThreshold() {
        submit(U1, activeJourney(U1));
        PassengerJourney j2 = activeJourney(U2);
        PassengerJourney j3 = activeJourney(U3);
        submit(U2, j2);
        clock.advance(Duration.ofMinutes(5).minusMillis(1));

        RegisterPassengerRequestResult third = submit(U3, j3);

        assertEquals("AWAITING_APPROVAL", third.groupStatus());
        assertEquals(3, third.contributorCount());
    }

    @Test
    void messageOfExactly500CharactersAndPhotoOfExactly5MiBAreAccepted() {
        PassengerJourney journey = activeJourney(U1);
        as(U1);

        RegisterPassengerRequestResult result = service.register(command(journey, UUID.randomUUID().toString(),
            "x".repeat(500), clock.instant(), TestImages.padded(PHOTO, FIVE_MIB)));

        assertEquals("COUNTED", result.status());
        assertEquals(FIVE_MIB, storedImageBytes(
            requestRepository.findById(result.requestId()).orElseThrow().getEvidenceImageId()).length);
    }

    // --- US08 S6 ---

    @Test
    void requestForAGroupAwaitingApprovalOrApprovedIsAttachedAndTheGroupIsUnchanged() {
        for (GroupStatus status : new GroupStatus[] {GroupStatus.AWAITING_APPROVAL, GroupStatus.APPROVED}) {
            PassengerRequestGroup group = groupInStatus(status);
            Long version = group.getVersion();
            long accounts = status == GroupStatus.APPROVED ? U4 : U1;
            PassengerJourney journey = activeJourney(accounts);

            RegisterPassengerRequestResult result = submit(accounts, journey);

            assertEquals("ATTACHED", result.status());
            assertEquals(status.name(), result.groupStatus());
            assertEquals(0, result.contributorCount());
            assertEquals(1, groupsOfShift().size());
            PassengerRequestGroup after = groupRepository.findById(group.getId()).orElseThrow();
            assertEquals(status, after.getStatus());
            assertEquals(version, after.getVersion());
            assertEquals(group.getId(), requestRepository.findById(result.requestId()).orElseThrow().getGroupId());

            requestRepository.deleteAll();
            groupRepository.delete(after);
        }
        assertEquals(0, emergencyRepository.count());
    }

    @Test
    void requestAfterANotApprovedOrClosedGroupStartsANewGroup() {
        for (GroupStatus status : new GroupStatus[] {GroupStatus.NOT_APPROVED, GroupStatus.CLOSED}) {
            PassengerRequestGroup old = groupInStatus(status);
            PassengerJourney journey = activeJourney(status == GroupStatus.CLOSED ? U2 : U1);

            RegisterPassengerRequestResult result = submit(status == GroupStatus.CLOSED ? U2 : U1, journey);

            assertEquals("COUNTED", result.status());
            assertEquals("COLLECTING", result.groupStatus());
            assertEquals(1, result.contributorCount());
            Long groupId = requestRepository.findById(result.requestId()).orElseThrow().getGroupId();
            assertTrue(!groupId.equals(old.getId()));
            assertEquals(status, groupRepository.findById(old.getId()).orElseThrow().getStatus());

            requestRepository.deleteAll();
            groupRepository.deleteAll(groupsOfShift());
        }
    }

    // --- US08 S3: rejections, one per code ---

    @Test
    void rejectsAnIdThatIsNotACanonicalUuid_includingOnesUuidFromStringWouldAccept() {
        PassengerJourney journey = activeJourney(U1);
        as(U1);

        for (String id : new String[] {"1-1-1-1-1", null, "", "not-a-uuid"}) {
            assertRejected(RuleViolationException.class, "INVALID_REQUEST_ID",
                command(journey, id, "m", clock.instant(), PHOTO));
        }
    }

    @Test
    void rejectsMissingBlankAndOverlongMessages() {
        PassengerJourney journey = activeJourney(U1);
        as(U1);
        String id = UUID.randomUUID().toString();

        assertRejected(RuleViolationException.class, "MESSAGE_REQUIRED", command(journey, id, null, clock.instant(), PHOTO));
        assertRejected(RuleViolationException.class, "MESSAGE_REQUIRED", command(journey, id, " \n\t ", clock.instant(), PHOTO));
        assertRejected(RuleViolationException.class, "MESSAGE_TOO_LONG",
            command(journey, id, "x".repeat(501), clock.instant(), PHOTO));
    }

    @Test
    void rejectsMissingEmptyUndecodableUnsupportedAndOversizedPhotos() {
        PassengerJourney journey = activeJourney(U1);
        as(U1);
        String id = UUID.randomUUID().toString();

        assertRejected(RuleViolationException.class, "EVIDENCE_PHOTO_REQUIRED", command(journey, id, "m", clock.instant(), null));
        assertRejected(RuleViolationException.class, "EVIDENCE_PHOTO_REQUIRED",
            command(journey, id, "m", clock.instant(), new byte[0]));
        assertRejected(RuleViolationException.class, "EVIDENCE_PHOTO_INVALID",
            command(journey, id, "m", clock.instant(), TestImages.gif()));
        assertRejected(RuleViolationException.class, "EVIDENCE_PHOTO_INVALID",
            command(journey, id, "m", clock.instant(), TestImages.truncatedJpeg()));
        assertRejected(RuleViolationException.class, "EVIDENCE_PHOTO_TOO_LARGE",
            command(journey, id, "m", clock.instant(), TestImages.padded(PHOTO, FIVE_MIB + 1)));
    }

    @Test
    void rejectsAMissingActivationTimeAndOneMoreThanFiveMinutesInTheFuture() {
        PassengerJourney journey = activeJourney(U1);
        as(U1);
        String id = UUID.randomUUID().toString();

        assertRejected(RuleViolationException.class, "INVALID_ACTIVATION_TIME", command(journey, id, "m", null, PHOTO));
        assertRejected(RuleViolationException.class, "INVALID_ACTIVATION_TIME",
            command(journey, id, "m", clock.instant().plus(Duration.ofMinutes(5)).plusMillis(1), PHOTO));
        assertEquals("COUNTED", service.register(command(journey, id, "m",
            clock.instant().plus(Duration.ofMinutes(5)), PHOTO)).status());
    }

    @Test
    void theFirstFailingCheckWins() {
        PassengerJourney journey = activeJourney(U1);
        as(U1);
        Instant badTime = clock.instant().plus(Duration.ofHours(1));

        assertRejected(RuleViolationException.class, "INVALID_REQUEST_ID", command(journey, "x", " ", badTime, null));
        assertRejected(RuleViolationException.class, "MESSAGE_REQUIRED",
            command(journey, UUID.randomUUID().toString(), " ", badTime, null));
        assertRejected(RuleViolationException.class, "EVIDENCE_PHOTO_REQUIRED",
            command(journey, UUID.randomUUID().toString(), "m", badTime, null));
    }

    @Test
    void aJourneyOfAnotherAccountAndAnUnknownJourneyAreIndistinguishable() {
        PassengerJourney others = activeJourney(U2);
        as(U1);

        ForbiddenOperationException other = assertRejected(ForbiddenOperationException.class, "JOURNEY_ACCESS_DENIED",
            command(others, UUID.randomUUID().toString(), "m", clock.instant(), PHOTO));
        ForbiddenOperationException unknown = assertRejected(ForbiddenOperationException.class, "JOURNEY_ACCESS_DENIED",
            new RegisterPassengerRequestCommand(UUID.randomUUID().toString(), 999_999_999L, "m",
                clock.instant(), PHOTO));

        assertEquals(other.getMessage(), unknown.getMessage());
    }

    @Test
    void anActivationAfterTheEndOfTheJourneyIsRejected() {
        PassengerJourney ended = endedJourney(U1, clock.instant().minusSeconds(120));
        as(U1);

        assertRejected(RuleViolationException.class, "ACTIVATION_AFTER_JOURNEY_END",
            command(ended, UUID.randomUUID().toString(), "m", clock.instant().minusSeconds(119), PHOTO));
    }

    @Test
    void aJourneyWhoseBusDoesNotExistIsRejected() {
        PassengerJourney orphan = journeyRepository.saveAndFlush(
            PassengerJourney.start(U1, 999_999_999L, shift.getId(), clock));
        try {
            as(U1);
            assertRejected(RuleViolationException.class, "BUS_NOT_FOUND",
                command(orphan, UUID.randomUUID().toString(), "m", clock.instant(), PHOTO));
        } finally {
            journeyRepository.delete(orphan);
        }
    }

    // --- US08 S3: retries and duplicates ---

    @Test
    void identicalRetryReturnsTheStoredRequestAndAddsNothing() {
        PassengerJourney journey = activeJourney(U1);
        RegisterPassengerRequestCommand command = valid(journey);
        as(U1);
        RegisterPassengerRequestResult first = service.register(command);
        clock.advance(Duration.ofSeconds(20));
        State before = state();

        RegisterPassengerRequestResult retry = service.register(command);

        assertTrue(retry.duplicate());
        assertEquals(first.requestId(), retry.requestId());
        assertEquals(first.status(), retry.status());
        assertEquals(first.receivedAt(), retry.receivedAt());
        assertEquals(before, state());
        assertEquals(1, requestRepository.count());
    }

    @Test
    void retryAfterAnotherAccountJoinedShowsTheNewCountWithTheOriginalStatus() {
        PassengerJourney j1 = activeJourney(U1);
        PassengerJourney j2 = activeJourney(U2);
        RegisterPassengerRequestCommand command = valid(j1);
        as(U1);
        RegisterPassengerRequestResult first = service.register(command);
        assertEquals(1, first.contributorCount());
        submit(U2, j2);

        as(U1);
        RegisterPassengerRequestResult retry = service.register(command);

        assertTrue(retry.duplicate());
        assertEquals("COUNTED", retry.status());
        assertEquals(first.receivedAt(), retry.receivedAt());
        assertEquals(2, retry.contributorCount());
        assertEquals("COLLECTING", retry.groupStatus());
    }

    @Test
    void anUppercaseCanonicalIdIsStoredInLowercaseAndTheLowercaseRetryIsADuplicate() {
        PassengerJourney journey = activeJourney(U1);
        String lower = UUID.randomUUID().toString();
        as(U1);

        RegisterPassengerRequestResult first = service.register(
            command(journey, lower.toUpperCase(), "m", clock.instant(), PHOTO));
        RegisterPassengerRequestResult retry = service.register(
            command(journey, lower, "m", clock.instant(), PHOTO));

        assertEquals(lower, first.requestId());
        assertTrue(requestRepository.existsById(lower));
        assertTrue(retry.duplicate());
        assertEquals(1, requestRepository.count());
    }

    @Test
    void sameIdWithADifferentMessageOrPhotoOrFromAnotherAccountIsRejectedAndStoresNothing() {
        PassengerJourney j1 = activeJourney(U1);
        PassengerJourney j2 = activeJourney(U2);
        String id = UUID.randomUUID().toString();
        Instant activatedAt = clock.instant().minusSeconds(10);
        as(U1);
        service.register(command(j1, id, "original", activatedAt, PHOTO));

        assertRejected(ConflictException.class, "REQUEST_ID_REUSED", command(j1, id, "changed", activatedAt, PHOTO));
        assertRejected(ConflictException.class, "REQUEST_ID_REUSED",
            command(j1, id, "original", activatedAt, TestImages.jpeg(9)));
        assertRejected(ConflictException.class, "REQUEST_ID_REUSED",
            command(j1, id, "original", activatedAt.plusMillis(1), PHOTO));
        as(U2);
        assertRejected(ConflictException.class, "REQUEST_ID_REUSED", command(j2, id, "original", activatedAt, PHOTO));
    }

    @Test
    void aSecondRequestOfTheSameAccountInTheSameGroupIsRejectedAndStoresNothing() {
        PassengerJourney journey = activeJourney(U1);
        submit(U1, journey);
        as(U1);

        assertRejected(ConflictException.class, "DUPLICATE_CONTRIBUTION", valid(journey));
        assertEquals(1, requestRepository.count());
    }

    @Test
    void aSecondRequestOfAContributorToAnAwaitingGroupIsAlsoRejected() {
        PassengerJourney j1 = activeJourney(U1);
        submit(U1, j1);
        submit(U2, activeJourney(U2));
        submit(U3, activeJourney(U3));
        as(U1);

        assertRejected(ConflictException.class, "DUPLICATE_CONTRIBUTION", valid(j1));
    }

    // --- US08 S5 ---

    @Test
    void aRequestActivatedMoreThanFiveMinutesAgoIsStoredAsLateWithoutAGroup() {
        PassengerJourney journey = activeJourney(U1);
        Instant activatedAt = clock.instant().minus(Duration.ofMinutes(10));
        as(U1);

        RegisterPassengerRequestResult result = service.register(
            command(journey, UUID.randomUUID().toString(), "late", activatedAt, PHOTO));

        assertEquals("LATE", result.status());
        assertNull(result.groupStatus());
        assertNull(result.contributorCount());
        assertEquals(0, groupsOfShift().size());
        PassengerRequest stored = requestRepository.findById(result.requestId()).orElseThrow();
        assertNull(stored.getGroupId());
        assertEquals(activatedAt, stored.getActivatedAt());
        assertEquals(clock.instant(), stored.getReceivedAt());
        assertArrayEquals(PHOTO, storedImageBytes(stored.getEvidenceImageId()));
        assertEquals(1, storedImagesAdded());
    }

    @Test
    void exactlyFiveMinutesOldIsEligibleAndOneMillisecondOlderIsLate() {
        PassengerJourney j1 = activeJourney(U1);
        PassengerJourney j2 = activeJourney(U2);
        as(U1);
        RegisterPassengerRequestResult exact = service.register(command(j1, UUID.randomUUID().toString(), "m",
            clock.instant().minus(Duration.ofMinutes(5)), PHOTO));
        as(U2);
        RegisterPassengerRequestResult older = service.register(command(j2, UUID.randomUUID().toString(), "m",
            clock.instant().minus(Duration.ofMinutes(5)).minusMillis(1), PHOTO));

        assertEquals("COUNTED", exact.status());
        assertEquals("LATE", older.status());
        assertEquals(1, groupsOfShift().size());
    }

    @Test
    void aRequestForAnEndedJourneyActivatedBeforeItsEndIsStoredAsLate() {
        PassengerJourney ended = endedJourney(U1, clock.instant().minusSeconds(10));
        as(U1);

        RegisterPassengerRequestResult result = service.register(command(ended, UUID.randomUUID().toString(), "m",
            clock.instant().minusSeconds(60), PHOTO));

        assertEquals("LATE", result.status());
        assertEquals(0, groupsOfShift().size());
    }
}
