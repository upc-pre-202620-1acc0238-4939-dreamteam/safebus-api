package com.dreamteam.safebus.safetycase.domain.repository;

import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PassengerRequestRepositoryTest {

    private static final Instant NOW = Instant.parse("2030-06-01T10:00:00.123Z");
    private static final long GROUP = 92001L;

    @Autowired PassengerRequestRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    private PassengerRequest save(Long groupId, long passenger, RequestStatus status) {
        return repository.saveAndFlush(PassengerRequest.create(UUID.randomUUID().toString(), groupId, passenger,
            1L, 2L, 3L, 4L, "message", 5L, "c".repeat(64), NOW.minusSeconds(10), NOW, status));
    }

    @Test
    void savedRequestIsReloadedWithEveryFieldAndIsNoLongerNew() {
        PassengerRequest saved = save(GROUP, 1L, RequestStatus.COUNTED);

        PassengerRequest loaded = repository.findById(saved.getId()).orElseThrow();

        assertFalse(loaded.isNew());
        assertEquals(GROUP, loaded.getGroupId());
        assertEquals(1L, loaded.getPassengerUserId());
        assertEquals("message", loaded.getMessage());
        assertEquals("c".repeat(64), loaded.getEvidenceSha256());
        assertEquals(NOW.minusSeconds(10), loaded.getActivatedAt());
        assertEquals(NOW, loaded.getReceivedAt());
        assertEquals(RequestStatus.COUNTED, loaded.getStatus());
    }

    @Test
    void persistingANewRequestWithAnExistingIdFailsInsteadOfOverwritingTheRow() {
        PassengerRequest first = save(GROUP, 1L, RequestStatus.COUNTED);
        PassengerRequest sameId = PassengerRequest.create(first.getId(), GROUP + 1, 2L,
            1L, 2L, 3L, 4L, "other message", 5L, "d".repeat(64), NOW, NOW, RequestStatus.COUNTED);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(sameId));

        assertEquals("message", repository.findById(first.getId()).orElseThrow().getMessage());
    }

    @Test
    void twoRequestsOfOneAccountInOneGroupViolateTheUniqueConstraint() {
        save(GROUP, 1L, RequestStatus.COUNTED);

        assertThrows(DataIntegrityViolationException.class, () -> save(GROUP, 1L, RequestStatus.ATTACHED));
        assertEquals(1, repository.count());
    }

    @Test
    void theSameAccountMayHaveRequestsInDifferentGroupsAndSeveralLateRequestsWithoutGroup() {
        save(GROUP, 1L, RequestStatus.COUNTED);
        save(GROUP + 1, 1L, RequestStatus.COUNTED);
        save(null, 1L, RequestStatus.LATE);
        save(null, 1L, RequestStatus.LATE);

        assertEquals(4, repository.count());
    }

    @Test
    void existsByGroupIdAndPassengerUserId_matchesOnlyThatAccountInThatGroup() {
        save(GROUP, 1L, RequestStatus.COUNTED);
        save(null, 2L, RequestStatus.LATE);

        assertTrue(repository.existsByGroupIdAndPassengerUserId(GROUP, 1L));
        assertFalse(repository.existsByGroupIdAndPassengerUserId(GROUP, 2L));
        assertFalse(repository.existsByGroupIdAndPassengerUserId(GROUP + 1, 1L));
    }

    @Test
    void countByGroupIdAndStatus_countsOnlyTheRequestedStatusOfThatGroup() {
        save(GROUP, 1L, RequestStatus.COUNTED);
        save(GROUP, 2L, RequestStatus.COUNTED);
        save(GROUP, 3L, RequestStatus.ATTACHED);
        save(GROUP + 1, 4L, RequestStatus.COUNTED);
        save(null, 5L, RequestStatus.LATE);

        assertEquals(2, repository.countByGroupIdAndStatus(GROUP, RequestStatus.COUNTED));
        assertEquals(1, repository.countByGroupIdAndStatus(GROUP, RequestStatus.ATTACHED));
        assertEquals(0, repository.countByGroupIdAndStatus(GROUP, RequestStatus.LATE));
    }
}
