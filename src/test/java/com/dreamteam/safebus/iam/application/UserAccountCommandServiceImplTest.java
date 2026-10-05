package com.dreamteam.safebus.iam.application;

import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountCommandServiceImplTest {

    @Mock
    UserAccountRepository repository;

    UserAccountCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAccountCommandServiceImpl(repository, new BCryptPasswordEncoder(), Clock.systemUTC());
    }

    @Test
    void createPassenger_concurrentDuplicateInsert_throwsLoginIdTaken() {
        when(repository.existsByLoginId(any())).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("unique constraint"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.createPassenger("race-pax", "Password1!"));
        assertEquals("LOGIN_ID_TAKEN", ex.code());
    }
}
