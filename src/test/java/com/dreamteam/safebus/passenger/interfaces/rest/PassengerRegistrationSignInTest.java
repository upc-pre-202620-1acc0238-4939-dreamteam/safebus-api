package com.dreamteam.safebus.passenger.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.iam.interfaces.acl.IamContextFacade;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PassengerRegistrationSignInTest {

    private static final String PASSENGERS_URL = "/api/v1/passengers";
    private static final String SIGN_IN_URL = "/api/v1/auth/sign-in";
    private static final String TEST_DNI = "12345678";

    @Autowired MockMvc mockMvc;
    @Autowired PassengerAccountRepository passengerAccountRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired StoredImageRepository storedImageRepository;
    @Autowired IamContextFacade iamFacade;
    @Autowired CompanyRepository companyRepository;

    @AfterEach
    void tearDown() {
        passengerAccountRepository.deleteAll();
        userAccountRepository.findByLoginId(TEST_DNI).ifPresent(userAccountRepository::delete);
        storedImageRepository.deleteAll();
    }

    // 4a: register with spaced DNI, sign in with trimmed and spaced DNI; role=PASSENGER, no companyId
    @Test
    void register_spacedDni_signInWithTrimmedAndSpacedDni_rolePassengerNoCompanyId() throws Exception {
        mockMvc.perform(multipart(PASSENGERS_URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", " " + TEST_DNI + " ")
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isCreated());

        // Trimmed DNI is the login
        mockMvc.perform(post(SIGN_IN_URL)
                .contentType(APPLICATION_JSON)
                .content("{\"loginId\":\"" + TEST_DNI + "\",\"password\":\"Password1!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("PASSENGER"));

        // IAM normalizes loginId on sign-in — spaced DNI also works
        mockMvc.perform(post(SIGN_IN_URL)
                .contentType(APPLICATION_JSON)
                .content("{\"loginId\":\" " + TEST_DNI + " \",\"password\":\"Password1!\"}"))
            .andExpect(status().isOk());

        var ua = userAccountRepository.findByLoginId(TEST_DNI)
            .orElseThrow(() -> new AssertionError("UserAccount not found"));
        assertEquals("PASSENGER", ua.getRole().name());
        assertNull(ua.getCompanyId());
    }

    // 4b: extra loginId param is silently ignored; account login = DNI
    @Test
    void register_extraLoginIdParam_accountCreatedWithDniAsLogin() throws Exception {
        mockMvc.perform(multipart(PASSENGERS_URL)
                .file(facePhotoJpeg())
                .param("loginId", "someone")  // extra, must be ignored
                .param("password", "Password1!")
                .param("dni", TEST_DNI)
                .param("termsAccepted", "true")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isCreated());

        // The ignored loginId cannot sign in
        mockMvc.perform(post(SIGN_IN_URL)
                .contentType(APPLICATION_JSON)
                .content("{\"loginId\":\"someone\",\"password\":\"Password1!\"}"))
            .andExpect(status().isUnauthorized());

        // The DNI is the real login
        mockMvc.perform(post(SIGN_IN_URL)
                .contentType(APPLICATION_JSON)
                .content("{\"loginId\":\"" + TEST_DNI + "\",\"password\":\"Password1!\"}"))
            .andExpect(status().isOk());

        assertFalse(userAccountRepository.existsByLoginId("someone"));
    }

    // 4c: IAM loginId already taken by a driver account — returns same 409 as the pre-check path
    @Test
    void register_iamLoginAlreadyTakenByDriver_returns409DniAlreadyRegistered() throws Exception {
        Company company = companyRepository.save(Company.create("Collision Co"));
        Long driverUaId = iamFacade.createDriverAccount(TEST_DNI, "DriverPass1!", company.getId());
        long imagesBefore = storedImageRepository.count();

        try {
            mockMvc.perform(multipart(PASSENGERS_URL)
                    .file(facePhotoJpeg())
                    .param("password", "Password1!")
                    .param("dni", TEST_DNI)
                    .param("termsAccepted", "true")
                    .param("termsVersion", "2026-10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DNI_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.detail").value("a passenger account with this DNI already exists"));

            assertEquals(0L, passengerAccountRepository.count());
            assertEquals(imagesBefore, storedImageRepository.count());
            assertEquals(1L, userAccountRepository.findAll().stream()
                .filter(u -> TEST_DNI.equals(u.getLoginId())).count());
        } finally {
            userAccountRepository.findById(driverUaId).ifPresent(userAccountRepository::delete);
            companyRepository.delete(company);
        }
    }

    // 4d-1: termsAccepted absent → TERMS_NOT_ACCEPTED
    @Test
    void register_termsAcceptedAbsent_returns422TermsNotAccepted() throws Exception {
        mockMvc.perform(multipart(PASSENGERS_URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", TEST_DNI)
                .param("termsVersion", "2026-10"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
    }

    // 4d-2: termsAccepted="false" → TERMS_NOT_ACCEPTED
    @Test
    void register_termsAcceptedFalse_returns422TermsNotAccepted() throws Exception {
        mockMvc.perform(multipart(PASSENGERS_URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", TEST_DNI)
                .param("termsAccepted", "false")
                .param("termsVersion", "2026-10"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
    }

    // 4d-3: termsAccepted="true" with wrong termsVersion → TERMS_VERSION_INVALID
    @Test
    void register_termsAcceptedTrueWrongVersion_returns422TermsVersionInvalid() throws Exception {
        mockMvc.perform(multipart(PASSENGERS_URL)
                .file(facePhotoJpeg())
                .param("password", "Password1!")
                .param("dni", TEST_DNI)
                .param("termsAccepted", "true")
                .param("termsVersion", "2025-01"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TERMS_VERSION_INVALID"));
    }

    private static MockMultipartFile facePhotoJpeg() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        return new MockMultipartFile("facePhoto", "photo.jpg", "image/jpeg", out.toByteArray());
    }
}
