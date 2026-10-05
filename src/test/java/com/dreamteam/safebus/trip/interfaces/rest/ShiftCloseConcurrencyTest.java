package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.AssignmentStatus;
import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ShiftCloseConcurrencyTest extends AbstractShiftCloseRestTest {

    private static final int THREADS = 8;
    // The first requests of a cold JVM run one after another; repeating on fresh shifts makes the overlap reliable,
    // so removing the row lock from the service makes this test fail.
    private static final int ROUNDS = 10;

    @Test
    void eightSimultaneousCloses_allReturn200WithTheSameClosedAtAndCloseOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            closeConcurrently(round);
        }
    }

    private void closeConcurrently(int round) throws Exception {
        DriverShift roundShift = activeShift(driver, otherBus);
        PassengerJourney j1 = journeyRepository.saveAndFlush(PassengerJourney.start(
            PASSENGER_USER + round * 10L, otherBus.getId(), roundShift.getId(), clock));
        PassengerJourney j2 = journeyRepository.saveAndFlush(PassengerJourney.start(
            PASSENGER_USER + round * 10L + 1, otherBus.getId(), roundShift.getId(), clock));

        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return mockMvc.perform(post(closeUrl(roundShift.getId())).with(driverJwt(DRIVER_USER)))
                        .andReturn();
                }));
            }
            List<String> bodies = new ArrayList<>();
            for (Future<MvcResult> f : futures) {
                MvcResult result = f.get();
                assertEquals(200, result.getResponse().getStatus(), "round " + round);
                bodies.add(result.getResponse().getContentAsString());
            }
            assertEquals(1, bodies.stream().distinct().count(),
                "round " + round + ": all responses must carry the same closedAt");
        } finally {
            pool.shutdownNow();
        }

        DriverShift stored = driverShiftRepository.findById(roundShift.getId()).orElseThrow();
        assertEquals(ShiftStatus.CLOSED, stored.getStatus());
        assertEquals(AssignmentStatus.CLOSED,
            assignmentRepository.findById(roundShift.getAssignmentId()).orElseThrow().getStatus());
        for (Long id : new Long[] {j1.getId(), j2.getId()}) {
            PassengerJourney j = journeyRepository.findById(id).orElseThrow();
            assertEquals(JourneyStatus.ENDED, j.getStatus());
            assertEquals(JourneyEndReason.SHIFT_CLOSED, j.getEndReason());
            assertEquals(stored.getClosedAt(), j.getEndedAt(),
                "round " + round + ": journeys must be ended once, at the closure time");
            assertNull(j.getActiveMarker());
        }
    }
}
