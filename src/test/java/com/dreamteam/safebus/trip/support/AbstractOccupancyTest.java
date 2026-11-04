package com.dreamteam.safebus.trip.support;

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
import com.dreamteam.safebus.safetycase.support.MutableClock;
import com.dreamteam.safebus.safetycase.support.MutableClockConfig;
import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.CountingSource;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.port.CountingSourceKeys;
import com.dreamteam.safebus.trip.domain.repository.BusOccupancyRepository;
import com.dreamteam.safebus.trip.domain.repository.CountingSourceRepository;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClockConfig.class)
public abstract class AbstractOccupancyTest {

    public static final String KEY_HEADER = "X-Counting-Source-Key";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected MutableClock clock;
    @Autowired protected CompanyRepository companyRepository;
    @Autowired protected BusRepository busRepository;
    @Autowired protected DriverRepository driverRepository;
    @Autowired protected RouteRepository routeRepository;
    @Autowired protected ShiftAssignmentRepository assignmentRepository;
    @Autowired protected DriverShiftRepository shiftRepository;
    @Autowired protected CountingSourceRepository sourceRepository;
    @Autowired protected BusOccupancyRepository occupancyRepository;
    @Autowired protected CountingSourceKeys keys;
    @Autowired protected JdbcTemplate jdbcTemplate;

    protected Company company;
    protected Bus bus;
    protected Driver driver;
    protected Route route;
    protected DriverShift shift;
    protected CountingSource source;
    protected String rawKey;

    private final List<ShiftAssignment> assignments = new ArrayList<>();
    private final List<DriverShift> shifts = new ArrayList<>();
    private final List<Bus> extraBuses = new ArrayList<>();

    @BeforeEach
    void createOccupancyFixture() {
        clock.set(MutableClockConfig.START);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        company = companyRepository.save(Company.create("OC Company " + suffix));
        bus = busRepository.save(Bus.create(company.getId(), "OC-" + suffix, () -> "oc-qr-" + suffix));
        driver = driverRepository.save(Driver.create(company.getId(),
            8_000_000L + Math.abs(suffix.hashCode() % 900_000), "OC Driver", () -> "OC-DRV-" + suffix,
            Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "OC Route " + suffix, "A", "B"));
        shift = startShiftOn(bus);
        CountingSourceKeys.GeneratedKey generated = keys.generate();
        rawKey = generated.rawKey();
        source = sourceRepository.save(CountingSource.register(company.getId(), bus.getId(), "door",
            generated.hash(), 1L, clock));
        occupancyRepository.save(BusOccupancy.create(company.getId(), bus.getId(), source.getId()));
    }

    @AfterEach
    void deleteOccupancyFixture() {
        List<Bus> all = new ArrayList<>(extraBuses);
        all.add(bus);
        for (Bus b : all) {
            jdbcTemplate.update("delete from occupancy_event where bus_id = ?", b.getId());
            occupancyRepository.findByBusId(b.getId()).ifPresent(occupancyRepository::delete);
            sourceRepository.deleteAll(sourceRepository.findByBusIdOrderByIdAsc(b.getId()));
        }
        shiftRepository.deleteAll(shifts);
        assignmentRepository.deleteAll(assignments);
        driverRepository.delete(driver);
        busRepository.deleteAll(all);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    protected DriverShift startShiftOn(Bus onBus) {
        ShiftAssignment assignment = assignmentRepository.save(ShiftAssignment.create(driver.getId(),
            onBus.getId(), route.getId(), clock.instant().minusSeconds(3600), clock.instant().plusSeconds(3600),
            1L, clock));
        assignments.add(assignment);
        DriverShift started = shiftRepository.save(DriverShift.start(assignment.getId(), driver.getId(),
            onBus.getId(), route.getId(), clock));
        shifts.add(started);
        return started;
    }

    protected Bus anotherBusOfTheCompany() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Bus other = busRepository.save(Bus.create(company.getId(), "OX-" + suffix, () -> "ox-qr-" + suffix));
        extraBuses.add(other);
        return other;
    }

    protected void closeShift(DriverShift toClose) {
        toClose.close(clock.instant(), null, null);
        shiftRepository.save(toClose);
    }
}
