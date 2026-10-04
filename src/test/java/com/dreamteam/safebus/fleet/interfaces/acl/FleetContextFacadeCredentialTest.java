package com.dreamteam.safebus.fleet.interfaces.acl;

import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FleetContextFacadeCredentialTest {

    @Autowired FleetContextFacade facade;
    @Autowired DriverRepository driverRepository;
    @Autowired Clock clock;

    @Test
    void findDriverByQrCredential_validCredential_returnsDriverInfo() {
        Driver saved = driverRepository.save(
            Driver.create(10L, 99L, "Facade Driver", () -> "FACADE-QR-001",
                          Duration.ofDays(365), clock));

        var result = facade.findDriverByQrCredential("FACADE-QR-001");

        assertTrue(result.isPresent());
        FleetContextFacade.DriverInfo info = result.get();
        assertEquals(saved.getId(), info.driverId());
        assertEquals(99L, info.userAccountId());
        assertEquals(10L, info.companyId());
        assertTrue(info.enabled());
        assertEquals(saved.getQrCredentialExpiresAt(), info.credentialExpiresAt());
    }

    @Test
    void findDriverByQrCredential_unknownCredential_returnsEmpty() {
        var result = facade.findDriverByQrCredential("NONEXISTENT-QR");
        assertFalse(result.isPresent());
    }
}
