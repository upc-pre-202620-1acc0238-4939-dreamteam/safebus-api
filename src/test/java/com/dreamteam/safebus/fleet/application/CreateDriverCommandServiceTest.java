package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CreateDriverCommandServiceTest {

    @Autowired CreateDriverCommandService driverService;
    @Autowired DriverRepository driverRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(1L, "SUPERVISOR", 1L));
    }

    @AfterEach
    void tearDown() {
        driverRepository.deleteAll();
        for (String loginId : new String[]{"drv-atomicity-short", "drv-atomicity-dup"}) {
            userAccountRepository.findByLoginId(loginId).ifPresent(userAccountRepository::delete);
        }
    }

    @Test
    void shortPassword_neitherAccountNorDriverPersisted() {
        assertThrows(RuleViolationException.class, () ->
            driverService.create(new CreateDriverCommand("Test Driver", "drv-atomicity-short", "short")));

        assertFalse(userAccountRepository.existsByLoginId("drv-atomicity-short"));
        assertEquals(0, driverRepository.count());
    }

    @Test
    void duplicateLoginId_noExtraDriverPersisted() {
        driverService.create(new CreateDriverCommand("Driver One", "drv-atomicity-dup", "Safebus2024!"));
        long countAfterFirst = driverRepository.count();

        ConflictException ex = assertThrows(ConflictException.class, () ->
            driverService.create(new CreateDriverCommand("Driver Two", "drv-atomicity-dup", "Safebus2024!")));
        assertEquals("LOGIN_ID_TAKEN", ex.code());
        assertEquals(countAfterFirst, driverRepository.count());
    }
}
