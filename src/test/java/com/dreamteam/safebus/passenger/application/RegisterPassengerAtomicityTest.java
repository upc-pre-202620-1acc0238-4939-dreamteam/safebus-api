package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.passenger.domain.repository.PassengerAccountRepository;
import com.dreamteam.safebus.shared.domain.repository.StoredImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RegisterPassengerAtomicityTest {

    @Autowired RegisterPassengerCommandService commandService;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired StoredImageRepository storedImageRepository;

    @MockitoBean
    PassengerAccountRepository passengerAccountRepository;

    @Test
    void whenPassengerAccountSaveFails_neitherUserAccountNorImageArePersisted() throws Exception {
        when(passengerAccountRepository.existsByDni(any())).thenReturn(false);
        when(passengerAccountRepository.saveAndFlush(any()))
                .thenThrow(new RuntimeException("forced failure in saveAndFlush"));

        long imageCountBefore = storedImageRepository.count();

        assertThrows(RuntimeException.class, () ->
                commandService.register(new RegisterPassengerCommand(
                        "Password1!", "12345678", "true", "2026-10", validJpeg())));

        assertFalse(userAccountRepository.existsByLoginId("12345678"),
                "UserAccount must have been rolled back");
        assertEquals(imageCountBefore, storedImageRepository.count(),
                "StoredImage must have been rolled back");
    }

    private static byte[] validJpeg() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        return out.toByteArray();
    }
}
