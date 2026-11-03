package com.dreamteam.safebus.safetycase.domain.repository;

import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PassengerRequestGroupRepositoryTest {

    private static final Instant NOW = Instant.parse("2030-04-01T10:00:00Z");
    private static final long SHIFT = 91001L;

    @Autowired PassengerRequestGroupRepository repository;
    @Autowired TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        repository.deleteAll(repository.findByShiftIdOrderByIdAsc(SHIFT));
        repository.deleteAll(repository.findByShiftIdOrderByIdAsc(SHIFT + 1));
    }

    private PassengerRequestGroup save(long shiftId, Instant start, GroupStatus status) {
        PassengerRequestGroup g = PassengerRequestGroup.open(1L, 2L, shiftId, start);
        if (status == GroupStatus.EXPIRED) {
            g.expire();
        } else if (status != GroupStatus.COLLECTING) {
            ReflectionTestUtils.setField(g, "status", status);
            if (!status.isOpen()) {
                ReflectionTestUtils.setField(g, "openMarker", null);
            }
        }
        return repository.saveAndFlush(g);
    }

    @Test
    void twoOpenGroupsForOneShiftViolateTheUniqueConstraint() {
        save(SHIFT, NOW, GroupStatus.COLLECTING);

        assertThrows(DataIntegrityViolationException.class, () -> save(SHIFT, NOW, GroupStatus.COLLECTING));
    }

    @Test
    void twoGroupsWithTheSamePublicReferenceViolateTheUniqueIndex() {
        PassengerRequestGroup first = save(SHIFT, NOW.minusSeconds(900), GroupStatus.EXPIRED);

        PassengerRequestGroup clash = PassengerRequestGroup.open(1L, 2L, SHIFT, NOW.minusSeconds(800));
        ReflectionTestUtils.setField(clash, "publicReference", first.getPublicReference());
        clash.expire();

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(clash));
    }

    @Test
    void groupsWithoutAPublicReferenceCanCoexist() {
        for (int i = 0; i < 2; i++) {
            PassengerRequestGroup legacy = PassengerRequestGroup.open(1L, 2L, SHIFT, NOW.minusSeconds(900L - i));
            ReflectionTestUtils.setField(legacy, "publicReference", null);
            legacy.expire();
            repository.saveAndFlush(legacy);
        }

        assertEquals(2, repository.findByShiftIdOrderByIdAsc(SHIFT).size());
    }

    @Test
    void anOpenGroupAndAnyNumberOfClosedGroupsCanCoexistOnOneShift() {
        save(SHIFT, NOW.minusSeconds(900), GroupStatus.EXPIRED);
        save(SHIFT, NOW.minusSeconds(800), GroupStatus.EXPIRED);
        save(SHIFT, NOW.minusSeconds(700), GroupStatus.NOT_APPROVED);
        save(SHIFT, NOW.minusSeconds(600), GroupStatus.CLOSED);
        PassengerRequestGroup open = save(SHIFT, NOW, GroupStatus.COLLECTING);

        assertEquals(5, repository.findByShiftIdOrderByIdAsc(SHIFT).size());
        assertEquals(open.getId(), findOpen(SHIFT).getId());
    }

    @Test
    void findOpenByShiftIdForUpdate_returnsCollectingAwaitingAndApprovedGroups() {
        for (GroupStatus status : List.of(GroupStatus.COLLECTING, GroupStatus.AWAITING_APPROVAL, GroupStatus.APPROVED)) {
            PassengerRequestGroup g = save(SHIFT, NOW, status);

            assertEquals(g.getId(), findOpen(SHIFT).getId());
            assertEquals(status, findOpen(SHIFT).getStatus());

            repository.delete(g);
            repository.flush();
        }
    }

    @Test
    void findOpenByShiftIdForUpdate_neverReturnsClosedGroupsOrOtherShifts() {
        save(SHIFT, NOW, GroupStatus.EXPIRED);
        save(SHIFT, NOW, GroupStatus.NOT_APPROVED);
        save(SHIFT, NOW, GroupStatus.CLOSED);
        save(SHIFT + 1, NOW, GroupStatus.COLLECTING);

        assertTrue(transactionTemplate.execute(s -> repository.findOpenByShiftIdForUpdate(SHIFT)).isEmpty());
    }

    @Test
    void findOverdueCollectingForUpdate_usesAnInclusiveBoundaryAndOnlyCollectingGroups() {
        PassengerRequestGroup overdue = save(SHIFT, NOW.minusSeconds(301), GroupStatus.COLLECTING);
        PassengerRequestGroup exactly = save(SHIFT + 1, NOW.minusSeconds(300), GroupStatus.COLLECTING);

        List<Long> ids = transactionTemplate.execute(s ->
            repository.findOverdueCollectingForUpdate(NOW).stream().map(PassengerRequestGroup::getId).toList());
        assertTrue(ids.contains(overdue.getId()));
        assertTrue(ids.contains(exactly.getId()));

        List<Long> oneMillisecondEarlier = transactionTemplate.execute(s ->
            repository.findOverdueCollectingForUpdate(NOW.minusMillis(1)).stream()
                .map(PassengerRequestGroup::getId).toList());
        assertTrue(oneMillisecondEarlier.contains(overdue.getId()));
        assertTrue(!oneMillisecondEarlier.contains(exactly.getId()));
    }

    @Test
    void findOverdueCollectingForUpdate_ignoresAwaitingApprovalGroups() {
        PassengerRequestGroup awaiting = save(SHIFT, NOW.minusSeconds(3600), GroupStatus.AWAITING_APPROVAL);

        List<Long> ids = transactionTemplate.execute(s ->
            repository.findOverdueCollectingForUpdate(NOW).stream().map(PassengerRequestGroup::getId).toList());

        assertTrue(!ids.contains(awaiting.getId()));
    }

    private PassengerRequestGroup findOpen(long shiftId) {
        return transactionTemplate.execute(s -> repository.findOpenByShiftIdForUpdate(shiftId)).orElseThrow();
    }
}
