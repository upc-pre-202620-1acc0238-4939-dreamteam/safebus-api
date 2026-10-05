package com.dreamteam.safebus.iam.application;

import com.dreamteam.safebus.iam.domain.model.UserAccount;
import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.shared.domain.exceptions.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SignInCommandServiceTest {

    @Autowired
    private SignInCommandService signInService;

    @Autowired
    private UserAccountCommandService accountService;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        accountService.createSupervisor("sup-signin", "Password1!", 1L);
        accountService.createPassenger("pax-signin", "Password1!");
    }

    @Test
    void signIn_validSupervisor_returnsTokenWithClaims() {
        SignInResult result = signInService.signIn(new SignInCommand("sup-signin", "Password1!"));

        assertNotNull(result.accessToken());
        assertEquals("SUPERVISOR", result.role());
        assertTrue(result.expiresAt().isAfter(Instant.now()));

        Jwt jwt = jwtDecoder.decode(result.accessToken());
        assertEquals("safebus", jwt.getClaimAsString("iss"));
        assertEquals("SUPERVISOR", jwt.getClaimAsString("role"));
        Number companyId = jwt.getClaim("companyId");
        assertNotNull(companyId);
        assertEquals(1L, companyId.longValue());
        assertTrue(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toHours() == 12);
    }

    @Test
    void signIn_passenger_tokenHasNoCompanyId() {
        SignInResult result = signInService.signIn(new SignInCommand("pax-signin", "Password1!"));
        Jwt jwt = jwtDecoder.decode(result.accessToken());
        org.junit.jupiter.api.Assertions.assertNull(jwt.getClaim("companyId"));
    }

    @Test
    void signIn_unknownLoginId_throwsInvalidCredentials() {
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> signInService.signIn(new SignInCommand("nobody", "Password1!")));
        assertEquals("INVALID_CREDENTIALS", ex.code());
        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void signIn_wrongPassword_throwsInvalidCredentials() {
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> signInService.signIn(new SignInCommand("sup-signin", "WrongPass!")));
        assertEquals("INVALID_CREDENTIALS", ex.code());
        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void signIn_disabledAccount_throwsInvalidCredentials() {
        UserAccount account = accountRepository.findByLoginId("sup-signin").orElseThrow();
        account.disable();
        accountRepository.saveAndFlush(account);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> signInService.signIn(new SignInCommand("sup-signin", "Password1!")));
        assertEquals("INVALID_CREDENTIALS", ex.code());
        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void signIn_allInvalidCases_returnSameCodeAndMessage() {
        UnauthorizedException ex1 = assertThrows(UnauthorizedException.class,
                () -> signInService.signIn(new SignInCommand("nobody", "Password1!")));
        UnauthorizedException ex2 = assertThrows(UnauthorizedException.class,
                () -> signInService.signIn(new SignInCommand("sup-signin", "WrongPass!")));
        assertEquals(ex1.code(), ex2.code());
        assertEquals(ex1.getMessage(), ex2.getMessage());
    }
}
