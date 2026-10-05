package com.dreamteam.safebus.passenger.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.fleet.domain.repository.ShiftAssignmentRepository;
import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JourneyControllerTest {

    private static final String JOURNEYS_URL = "/api/v1/journeys";
    private static final Long PASSENGER_USER_ID = 7777L;
    private static final Instant T1 = Instant.parse("2030-01-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-01-01T20:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired PassengerJourneyRepository journeyRepository;
    @Autowired Clock clock;

    Company company;
    Bus bus;
    Driver driver;
    Route route;
    ShiftAssignment assignment;
    DriverShift shift;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Journey Test Co"));
        bus = busRepository.save(Bus.create(company.getId(), "JCT-BUS01", () -> "JCT-QR-001"));
        driver = driverRepository.save(Driver.create(company.getId(), 8888L, "JCT Driver",
            () -> "JCT-DRV-QR", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "JCT Route", "X", "Y"));
        assignment = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        shift = driverShiftRepository.save(
            DriverShift.start(assignment.getId(), driver.getId(), bus.getId(), route.getId(), clock));
    }

    @AfterEach
    void tearDown() {
        journeyRepository.deleteAll();
        driverShiftRepository.delete(shift);
        assignmentRepository.delete(assignment);
        driverRepository.delete(driver);
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor passengerJwt(Long userId) {
        return jwt()
            .jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
            .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"));
    }

    private static long journeyIdOf(MvcResult result) throws Exception {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"journeyId\":(\\d+)")
            .matcher(result.getResponse().getContentAsString());
        m.find();
        return Long.parseLong(m.group(1));
    }

    // --- US06 S1: start journey ---

    @Test
    void start_validQr_returns201WithJourneyId() throws Exception {
        mockMvc.perform(post(JOURNEYS_URL)
                .with(passengerJwt(PASSENGER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"busQrCode\":\"JCT-QR-001\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.journeyId").isNumber())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.startedAt").isNotEmpty())
            .andExpect(jsonPath("$.bus.plate").value("JCT-BUS01"))
            .andExpect(jsonPath("$.bus.companyName").value("Journey Test Co"))
            .andExpect(jsonPath("$.bus.companyValidationStatus").value("VALIDATED"))
            .andExpect(jsonPath("$.route.name").value("JCT Route"))
            .andExpect(jsonPath("$.route.origin").value("X"))
            .andExpect(jsonPath("$.route.destination").value("Y"))
            .andExpect(jsonPath("$.driverPublicName").value("JCT Driver"))
            .andExpect(jsonPath("$.id").doesNotExist())
            .andExpect(jsonPath("$.companyValidated").doesNotExist());
    }

    @Test
    void start_sameQrTwice_secondReturns200WithSameId() throws Exception {
        MvcResult first = mockMvc.perform(post(JOURNEYS_URL)
                .with(passengerJwt(PASSENGER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"busQrCode\":\"JCT-QR-001\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.bus.plate").value("JCT-BUS01"))
            .andReturn();

        String firstBody = first.getResponse().getContentAsString();

        MvcResult second = mockMvc.perform(post(JOURNEYS_URL)
                .with(passengerJwt(PASSENGER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"busQrCode\":\"JCT-QR-001\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bus.plate").value("JCT-BUS01"))
            .andReturn();

        assertEquals(firstBody, second.getResponse().getContentAsString());
    }

    // --- US06 S2: rejection cases ---

    @Test
    void start_unknownQr_returns422BusQrInvalid() throws Exception {
        mockMvc.perform(post(JOURNEYS_URL)
                .with(passengerJwt(PASSENGER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"busQrCode\":\"UNKNOWN\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUS_QR_INVALID"));
    }

    @Test
    void start_activeJourneyOnDifferentBus_returns409ActiveJourneyExists() throws Exception {
        Bus bus2 = busRepository.save(Bus.create(company.getId(), "JCT-BUS02", () -> "JCT-QR-002"));
        ShiftAssignment assignment2 = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus2.getId(), route.getId(), T1, T2, 1L, clock));
        DriverShift shift2 = driverShiftRepository.save(
            DriverShift.start(assignment2.getId(), driver.getId(), bus2.getId(), route.getId(), clock));
        try {
            // Start on first bus
            mockMvc.perform(post(JOURNEYS_URL)
                    .with(passengerJwt(PASSENGER_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"busQrCode\":\"JCT-QR-001\"}"))
                .andExpect(status().isCreated());

            // Try to start on second bus
            mockMvc.perform(post(JOURNEYS_URL)
                    .with(passengerJwt(PASSENGER_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"busQrCode\":\"JCT-QR-002\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_JOURNEY_EXISTS"));
        } finally {
            journeyRepository.deleteAll();
            driverShiftRepository.delete(shift2);
            assignmentRepository.delete(assignment2);
            busRepository.delete(bus2);
        }
    }

    @Test
    void start_noToken_returns401() throws Exception {
        mockMvc.perform(post(JOURNEYS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"busQrCode\":\"JCT-QR-001\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void start_supervisorRole_returns403() throws Exception {
        mockMvc.perform(post(JOURNEYS_URL)
                .with(jwt().jwt(b -> b.subject("1").claim("role", "SUPERVISOR"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"busQrCode\":\"JCT-QR-001\"}"))
            .andExpect(status().isForbidden());
    }

    // --- US06 S4: end journey ---

    @Test
    void end_activeJourney_returns200WithEndedJourney() throws Exception {
        // Start first
        MvcResult startResult = mockMvc.perform(post(JOURNEYS_URL)
                .with(passengerJwt(PASSENGER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"busQrCode\":\"JCT-QR-001\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        long journeyId = journeyIdOf(startResult);

        mockMvc.perform(post(JOURNEYS_URL + "/" + journeyId + "/end")
                .with(passengerJwt(PASSENGER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"MANUAL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.journeyId").value(journeyId))
            .andExpect(jsonPath("$.status").value("ENDED"))
            .andExpect(jsonPath("$.endedAt").isNotEmpty())
            .andExpect(jsonPath("$.endReason").value("MANUAL"))
            .andExpect(jsonPath("$.changed").doesNotExist());
    }

    @Test
    void end_alreadyEnded_returns200WithOriginalEndData() throws Exception {
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(PASSENGER_USER_ID, bus.getId(), shift.getId(), clock));
        journey.end(JourneyEndReason.MANUAL, Instant.now(clock));
        journey = journeyRepository.saveAndFlush(journey);

        mockMvc.perform(post(JOURNEYS_URL + "/" + journey.getId() + "/end")
                .with(passengerJwt(PASSENGER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SIGN_OUT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.journeyId").value(journey.getId()))
            .andExpect(jsonPath("$.status").value("ENDED"))
            .andExpect(jsonPath("$.endedAt").value(journey.getEndedAt().toString()))
            .andExpect(jsonPath("$.endReason").value("MANUAL"));
    }

    @Test
    void end_wrongOwner_returns403JourneyAccessDenied() throws Exception {
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(PASSENGER_USER_ID, bus.getId(), shift.getId(), clock));

        mockMvc.perform(post(JOURNEYS_URL + "/" + journey.getId() + "/end")
                .with(passengerJwt(9999L)) // different user
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"MANUAL\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("JOURNEY_ACCESS_DENIED"));
    }

    @Test
    void end_invalidReason_returns422InvalidEndReason() throws Exception {
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(PASSENGER_USER_ID, bus.getId(), shift.getId(), clock));

        mockMvc.perform(post(JOURNEYS_URL + "/" + journey.getId() + "/end")
                .with(passengerJwt(PASSENGER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"AUTOMATIC_SEPARATION\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_END_REASON"));
    }

    // --- optional bodies ---

    private long seedActiveJourney() {
        return journeyRepository.saveAndFlush(
            PassengerJourney.start(PASSENGER_USER_ID, bus.getId(), shift.getId(), clock)).getId();
    }

    private void assertEndedWithManual(long journeyId, org.springframework.test.web.servlet.ResultActions result)
            throws Exception {
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.journeyId").value(journeyId))
            .andExpect(jsonPath("$.status").value("ENDED"))
            .andExpect(jsonPath("$.endReason").value("MANUAL"));
        assertEquals(JourneyEndReason.MANUAL, journeyRepository.findById(journeyId).orElseThrow().getEndReason());
    }

    @Test
    void end_noBody_endsWithManual() throws Exception {
        long id = seedActiveJourney();
        assertEndedWithManual(id, mockMvc.perform(post(JOURNEYS_URL + "/" + id + "/end")
            .with(passengerJwt(PASSENGER_USER_ID))));
    }

    @Test
    void end_emptyJsonObject_endsWithManual() throws Exception {
        long id = seedActiveJourney();
        assertEndedWithManual(id, mockMvc.perform(post(JOURNEYS_URL + "/" + id + "/end")
            .with(passengerJwt(PASSENGER_USER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")));
    }

    @Test
    void end_nullReason_endsWithManual() throws Exception {
        long id = seedActiveJourney();
        assertEndedWithManual(id, mockMvc.perform(post(JOURNEYS_URL + "/" + id + "/end")
            .with(passengerJwt(PASSENGER_USER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":null}")));
    }

    @Test
    void start_noBody_returns422BusQrInvalid() throws Exception {
        mockMvc.perform(post(JOURNEYS_URL)
                .with(passengerJwt(PASSENGER_USER_ID)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUS_QR_INVALID"));
        assertEquals(0, journeyRepository.count());
    }
}
