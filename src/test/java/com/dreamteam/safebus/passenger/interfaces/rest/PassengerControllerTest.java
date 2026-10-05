package com.dreamteam.safebus.passenger.interfaces.rest;

import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.passenger.domain.repository.PassengerAccountRepository;
import com.dreamteam.safebus.shared.domain.repository.StoredImageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PassengerControllerTest {

    private static final String URL = "/api/v1/passengers";

    @Autowired MockMvc mockMvc;
    @Autowired PassengerAccountRepository passengerAccountRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired StoredImageRepository storedImageRepository;

    private static final List<String> TEST_DNIS = List.of(
        "11223344",
        "55667788",
        "99887766",
        "77665544"
    );

    @AfterEach
    void tearDown() {
        passengerAccountRepository.deleteAll();
        for (String dni : TEST_DNIS) {
            userAccountRepository.findByLoginId(dni).ifPresent(userAccountRepository::delete);
        }
        storedImageRepository.deleteAll();
    }

    // --- US23 S1: successful registration ---

    @Test
    void register_validJpeg_returns201WithId() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", "11223344")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void register_validPng_returns201() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(facePhotoPng())
                .param("password", "Password1!")
                .param("dni", "55667788")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void register_passwordStoredAsBcryptHash() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", "11223344")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isCreated());

        var account = userAccountRepository.findByLoginId("11223344")
            .orElseThrow(() -> new AssertionError("UserAccount not found"));
        assertTrue(account.getPasswordHash().startsWith("$2"),
            "password hash must be a BCrypt hash (starts with $2)");
    }

    @Test
    void register_passengerRoleNoCompanyId() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", "11223344")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isCreated());

        var account = userAccountRepository.findByLoginId("11223344")
            .orElseThrow(() -> new AssertionError("UserAccount not found"));
        assertEquals("PASSENGER", account.getRole().name());
        assertNull(account.getCompanyId());
    }

    // --- US23 S2: rejection cases ---

    @Test
    void register_invalidDni_returns422InvalidDni() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", "1234567")  // 7 digits
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_DNI"));
    }

    @Test
    void register_nullPassword_returns422PasswordRequired() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(facePhotoJpeg())
                // no password param — DNI/terms/photo pass; IAM layer rejects null password
                .param("dni", "11223344")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));
    }

    @Test
    void register_termsNotAccepted_returns422() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", "11223344")
                // no termsAccepted part — triggers TERMS_NOT_ACCEPTED
                .param("termsVersion", "2026-10"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
    }

    @Test
    void register_wrongTermsVersion_returns422TermsVersionInvalid() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", "11223344")
                .param("termsAccepted", "true")
                .param("termsVersion", "2025-01"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TERMS_VERSION_INVALID"));
    }

    @Test
    void register_noFacePhoto_returns422FacePhotoRequired() throws Exception {
        mockMvc.perform(multipart(URL)
                .param("password", "Password1!")
                .param("dni", "11223344")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("FACE_PHOTO_REQUIRED"));
    }

    @Test
    void register_gifFacePhoto_returns422FacePhotoInvalid() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(new MockMultipartFile("facePhoto", "photo.gif", "image/gif", gifBytes()))
                .param("password", "Password1!")
                .param("dni", "11223344")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("FACE_PHOTO_INVALID"));
    }

    @Test
    void register_atomicity_noPartialDataOnFailure() throws Exception {
        long imagesBefore = storedImageRepository.count();

        mockMvc.perform(multipart(URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", "1234567") // invalid DNI triggers 422, no writes at all
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isUnprocessableEntity());

        assertFalse(userAccountRepository.existsByLoginId("1234567"));
        assertEquals(imagesBefore, storedImageRepository.count());
    }

    // --- US23 S3: duplicate DNI with spaces ---

    @Test
    void register_duplicateDni_returns409DniAlreadyRegistered() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", "99887766")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isCreated());

        mockMvc.perform(multipart(URL)
                .file(facePhotoPng())
                .param("password", "DifferentPass2!")
                .param("dni", "  99887766  ")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DNI_ALREADY_REGISTERED"));
    }

    @Test
    void register_duplicateDni_firstAccountUnchanged() throws Exception {
        mockMvc.perform(multipart(URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", "77665544")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isCreated());

        var firstAccount = userAccountRepository.findByLoginId("77665544")
            .orElseThrow(() -> new AssertionError("First account not found"));
        String firstHash = firstAccount.getPasswordHash();

        mockMvc.perform(multipart(URL)
                .file(facePhotoPng())
                .param("password", "AnotherPass3!")
                .param("dni", "77665544")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isConflict());

        var reloaded = userAccountRepository.findByLoginId("77665544").orElseThrow();
        assertEquals(firstHash, reloaded.getPasswordHash());
        assertEquals(1L, userAccountRepository.findAll().stream()
            .filter(u -> "77665544".equals(u.getLoginId())).count());
    }

    // Helpers

    private static MockMultipartFile facePhotoJpeg() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        return new MockMultipartFile("facePhoto", "photo.jpg", "image/jpeg", out.toByteArray());
    }

    private static MockMultipartFile facePhotoPng() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile("facePhoto", "photo.png", "image/png", out.toByteArray());
    }

    private static byte[] gifBytes() {
        return new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x3B};
    }
}
