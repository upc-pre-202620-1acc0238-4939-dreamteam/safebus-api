package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.ConsistencyState;
import com.dreamteam.safebus.trip.support.AbstractOccupancyTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OccupancyEventConcurrencyTest extends AbstractOccupancyTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 30;
    private static final String EVENTS = "/api/v1/occupancy-events";

    private void startRound() throws Exception {
        jdbcTemplate.update("delete from occupancy_event where bus_id = ?", bus.getId());
        BusOccupancy row = occupancyRepository.findByBusId(bus.getId()).orElseThrow();
        row.resetForNewSource(source.getId());
        occupancyRepository.saveAndFlush(row);
        String body = "{\"initializationId\":\"" + UUID.randomUUID() + "\",\"busId\":" + bus.getId()
            + ",\"shiftId\":" + shift.getId() + ",\"confirmedCount\":0,\"confirmedAt\":\"" + clock.instant() + "\"}";
        mockMvc.perform(post("/api/v1/occupancy-initializations").header(KEY_HEADER, rawKey)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    private String body(String eventId, long sequence) {
        return "{\"eventId\":\"" + eventId + "\",\"busId\":" + bus.getId() + ",\"shiftId\":" + shift.getId()
            + ",\"sequence\":" + sequence + ",\"type\":\"ENTRY\",\"capturedAt\":\"" + clock.instant() + "\"}";
    }

    private int send(String body) throws Exception {
        return mockMvc.perform(post(EVENTS).header(KEY_HEADER, rawKey).contentType(MediaType.APPLICATION_JSON)
            .content(body)).andReturn().getResponse().getStatus();
    }

    private List<Integer> fireTogether(List<String> bodies) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(bodies.size());
        CyclicBarrier barrier = new CyclicBarrier(bodies.size());
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (String body : bodies) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return send(body);
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private long storedEvents() {
        return jdbcTemplate.queryForObject("select count(*) from occupancy_event where bus_id = ?",
            Long.class, bus.getId());
    }

    private BusOccupancy stored() {
        return occupancyRepository.findByBusId(bus.getId()).orElseThrow();
    }

    @Test
    void identicalRetriesApplyOnceAcrossManyRounds() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            startRound();
            String body = body(UUID.randomUUID().toString(), 1);

            List<Integer> statuses = fireTogether(Collections.nCopies(THREADS, body));

            assertThat(statuses.stream().filter(s -> s == 201).count()).as("round " + round).isEqualTo(1);
            assertThat(statuses.stream().filter(s -> s == 200).count()).as("round " + round).isEqualTo(THREADS - 1);
            assertThat(stored().getCount()).isEqualTo(1);
            assertThat(storedEvents()).isEqualTo(1);
        }
    }

    @Test
    void competingEventsForTheSameSequenceApplyOnceAcrossManyRounds() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            startRound();
            List<String> bodies = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                bodies.add(body(UUID.randomUUID().toString(), 1));
            }

            List<Integer> statuses = fireTogether(bodies);

            assertThat(statuses.stream().filter(s -> s == 201).count()).as("round " + round).isEqualTo(1);
            assertThat(statuses.stream().filter(s -> s == 409).count()).as("round " + round).isEqualTo(THREADS - 1);
            assertThat(stored().getCount()).isEqualTo(1);
            assertThat(storedEvents()).isEqualTo(1);
        }
    }

    @Test
    void shuffledSequencesNeverCorruptTheCountAndTheReplayRecoversIt() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            startRound();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                ids.add(UUID.randomUUID().toString());
            }
            List<Integer> order = new ArrayList<>();
            for (int i = 1; i <= THREADS; i++) {
                order.add(i);
            }
            Collections.shuffle(order);
            List<String> bodies = new ArrayList<>();
            for (int sequence : order) {
                bodies.add(body(ids.get(sequence - 1), sequence));
            }

            List<Integer> statuses = fireTogether(bodies);

            assertThat(statuses).as("round " + round).allMatch(s -> s == 201 || s == 409);
            long applied = statuses.stream().filter(s -> s == 201).count();
            assertThat(storedEvents()).as("round " + round).isEqualTo(applied);
            assertThat((long) stored().getCount()).isEqualTo(applied);
            assertThat(stored().getLastSequence()).isEqualTo(applied);

            for (int sequence = 1; sequence <= THREADS; sequence++) {
                assertThat(send(body(ids.get(sequence - 1), sequence))).isIn(200, 201);
            }

            BusOccupancy recovered = stored();
            assertThat(recovered.getCount()).as("round " + round).isEqualTo(THREADS);
            assertThat(recovered.getConsistency()).isEqualTo(ConsistencyState.CONSISTENT);
            assertThat(storedEvents()).isEqualTo(THREADS);
        }
    }
}
