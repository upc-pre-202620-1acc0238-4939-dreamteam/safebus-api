package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.passenger.domain.repository.PassengerAccountRepository;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.shared.domain.repository.StoredImageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RegisterPassengerPhotoLimitTest {

    private static final int FIVE_MIB = 5 * 1024 * 1024;
    private static final String DNI_OK = "55667788";
    private static final String DNI_TOO_LARGE = "55667799";

    @Autowired RegisterPassengerCommandService commandService;
    @Autowired PassengerAccountRepository passengerAccountRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired StoredImageRepository storedImageRepository;

    @AfterEach
    void cleanUp() {
        passengerAccountRepository.deleteAll();
        userAccountRepository.findByLoginId(DNI_OK).ifPresent(userAccountRepository::delete);
        userAccountRepository.findByLoginId(DNI_TOO_LARGE).ifPresent(userAccountRepository::delete);
        storedImageRepository.deleteAll();
    }

    @Test
    void register_photoPaddedToExactlyFiveMib_isAccepted() throws Exception {
        byte[] photo = Arrays.copyOf(validJpeg(), FIVE_MIB);

        RegisterPassengerResult result = commandService.register(
            new RegisterPassengerCommand("Password1!", DNI_OK, "true", "2026-10", photo));

        assertNotNull(result.passengerAccountId());
        assertEquals(1, passengerAccountRepository.count());
        assertTrue(userAccountRepository.existsByLoginId(DNI_OK));
        assertEquals(1, storedImageRepository.count());
    }

    @Test
    void register_photoPaddedToFiveMibPlusOne_failsWithFacePhotoTooLargeAndPersistsNothing() throws Exception {
        byte[] photo = Arrays.copyOf(validJpeg(), FIVE_MIB + 1);
        long accountsBefore = passengerAccountRepository.count();
        long imagesBefore = storedImageRepository.count();

        RuleViolationException ex = assertThrows(RuleViolationException.class, () ->
            commandService.register(
                new RegisterPassengerCommand("Password1!", DNI_TOO_LARGE, "true", "2026-10", photo)));

        assertEquals("FACE_PHOTO_TOO_LARGE", ex.code());
        assertEquals(accountsBefore, passengerAccountRepository.count());
        assertFalse(userAccountRepository.existsByLoginId(DNI_TOO_LARGE));
        assertEquals(imagesBefore, storedImageRepository.count());
    }

    private static byte[] validJpeg() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        return out.toByteArray();
    }
}
