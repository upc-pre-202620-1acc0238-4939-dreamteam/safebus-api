package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.port.QrCodeGenerator;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateBusCommandServiceTest {

    @Mock BusRepository busRepository;
    @Mock QrCodeGenerator qrCodeGenerator;
    @Mock CurrentUserProvider currentUserProvider;

    CreateBusCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreateBusCommandServiceImpl(busRepository, qrCodeGenerator, currentUserProvider);
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(1L, "SUPERVISOR", 1L));
    }

    @Test
    void create_validPlate_normalizedAndSaved() {
        when(qrCodeGenerator.generate()).thenReturn("test-qr");
        Bus saved = Bus.create(1L, "ABC-123", () -> "test-qr");
        when(busRepository.existsByPlate("ABC-123")).thenReturn(false);
        when(busRepository.save(any(Bus.class))).thenReturn(saved);

        Bus result = service.create(new CreateBusCommand("abc-123"));

        assertEquals("ABC-123", result.getPlate());
        verify(busRepository).save(any(Bus.class));
    }

    @Test
    void create_duplicatePlate_throwsPlateTaken() {
        when(busRepository.existsByPlate("ABC-123")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
            () -> service.create(new CreateBusCommand("ABC-123")));
        assertEquals("PLATE_TAKEN", ex.code());
        verify(busRepository, never()).save(any());
    }
}
