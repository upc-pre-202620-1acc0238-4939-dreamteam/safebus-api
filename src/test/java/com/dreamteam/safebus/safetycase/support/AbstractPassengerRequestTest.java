package com.dreamteam.safebus.safetycase.support;

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
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.shared.domain.repository.StoredImageRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@Import(MutableClockConfig.class)
public abstract class AbstractPassengerRequestTest {

    @Autowired protected MutableClock clock;
    @Autowired protected CompanyRepository companyRepository;
    @Autowired protected BusRepository busRepository;
    @Autowired protected DriverRepository driverRepository;
    @Autowired protected RouteRepository routeRepository;
    @Autowired protected ShiftAssignmentRepository assignmentRepository;
    @Autowired protected DriverShiftRepository shiftRepository;
    @Autowired protected PassengerJourneyRepository journeyRepository;
    @Autowired protected PassengerRequestRepository requestRepository;
    @Autowired protected PassengerRequestGroupRepository groupRepository;
    @Autowired protected EmergencyRepository emergencyRepository;
    @Autowired protected StoredImageRepository storedImageRepository;
    @Autowired protected JdbcTemplate jdbcTemplate;

    protected Company company;
    protected Bus bus;
    protected Driver driver;
    protected Route route;
    protected DriverShift shift;

    private final List<Long> journeyIds = new ArrayList<>();
    private long storedImagesBefore;
    private ShiftAssignment assignment;

    @BeforeEach
    void createFleetFixture() {
        clock.set(MutableClockConfig.START);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        company = companyRepository.save(Company.create("PR Company " + suffix));
        bus = busRepository.save(Bus.create(company.getId(), "PR-" + suffix, () -> "pr-qr-" + suffix));
        driver = driverRepository.save(Driver.create(company.getId(),
            9_000_000L + Math.abs(suffix.hashCode() % 900_000), "PR Driver", () -> "PR-DRV-" + suffix,
            Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "PR Route " + suffix, "A", "B"));
        assignment = assignmentRepository.save(ShiftAssignment.create(driver.getId(),
            bus.getId(), route.getId(), clock.instant().minusSeconds(3600), clock.instant().plusSeconds(3600),
            1L, clock));
        shift = shiftRepository.save(DriverShift.start(assignment.getId(), driver.getId(), bus.getId(),
            route.getId(), clock));
        storedImagesBefore = storedImageRepository.count();
    }

    @AfterEach
    void deleteFixture() {
        requestRepository.deleteAll();
        groupRepository.deleteAll(groupRepository.findByShiftIdOrderByIdAsc(shift.getId()));
        journeyRepository.deleteAllById(journeyIds);
        storedImageRepository.deleteAll();
        shiftRepository.delete(shift);
        assignmentRepository.delete(assignment);
        driverRepository.delete(driver);
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    protected PassengerJourney activeJourney(long userId) {
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(userId, bus.getId(), shift.getId(), clock));
        journeyIds.add(journey.getId());
        return journey;
    }

    protected PassengerJourney endedJourney(long userId, Instant endedAt) {
        PassengerJourney journey = PassengerJourney.start(userId, bus.getId(), shift.getId(), clock);
        journey.end(JourneyEndReason.MANUAL, endedAt);
        journey = journeyRepository.saveAndFlush(journey);
        journeyIds.add(journey.getId());
        return journey;
    }

    protected PassengerRequestGroup groupInStatus(GroupStatus status) {
        PassengerRequestGroup group = PassengerRequestGroup.open(company.getId(), bus.getId(), shift.getId(),
            clock.instant());
        ReflectionTestUtils.setField(group, "status", status);
        if (!status.isOpen()) {
            ReflectionTestUtils.setField(group, "openMarker", null);
        }
        return groupRepository.saveAndFlush(group);
    }

    protected long storedImagesAdded() {
        return storedImageRepository.count() - storedImagesBefore;
    }

    protected byte[] storedImageBytes(Long imageId) {
        return jdbcTemplate.queryForObject("select data from stored_image where id = ?", byte[].class, imageId);
    }

    protected List<PassengerRequestGroup> groupsOfShift() {
        return groupRepository.findByShiftIdOrderByIdAsc(shift.getId());
    }
}
