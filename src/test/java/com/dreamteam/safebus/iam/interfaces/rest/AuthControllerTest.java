package com.dreamteam.safebus.iam.interfaces.rest;

import com.dreamteam.safebus.iam.application.UserAccountCommandService;
import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    private static final String SIGN_IN_URL = "/api/v1/auth/sign-in";
    private static final String SIGN_OUT_URL = "/api/v1/auth/sign-out";
    private static final String TEST_SECRET = "test-secret-for-testing-purposes-only-32b";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserAccountCommandService accountService;

    @Autowired
    UserAccountRepository userAccountRepository;

    @BeforeEach
    void setUp() {
        accountService.createSupervisor("ctrl-sup", "Password1!", 1L);
        accountService.createPassenger("ctrl-pax", "Password1!");
    }

    @Test
    void signIn_validCredentials_returns200WithAllFields() throws Exception {
        mockMvc.perform(post(SIGN_IN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"ctrl-sup\",\"password\":\"Password1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("SUPERVISOR"))
                .andExpect(jsonPath("$.expiresAt").isString());
    }

    @Test
    void signIn_unknownLoginId_returns401WithCode() throws Exception {
        mockMvc.perform(post(SIGN_IN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"nobody\",\"password\":\"Password1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void signIn_wrongPassword_returns401WithCode() throws Exception {
        mockMvc.perform(post(SIGN_IN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"ctrl-sup\",\"password\":\"WrongPass!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void signIn_disabledAccount_returns401WithCode() throws Exception {
        var account = userAccountRepository.findByLoginId("ctrl-sup").orElseThrow();
        account.disable();
        userAccountRepository.saveAndFlush(account);

        mockMvc.perform(post(SIGN_IN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"ctrl-sup\",\"password\":\"Password1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void signIn_blankFields_returns422() throws Exception {
        mockMvc.perform(post(SIGN_IN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void signOut_validToken_returns204() throws Exception {
        String token = signInAndGetToken("ctrl-sup");

        mockMvc.perform(post(SIGN_OUT_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void signOut_noToken_returns401() throws Exception {
        mockMvc.perform(post(SIGN_OUT_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signOut_malformedToken_returns401() throws Exception {
        mockMvc.perform(post(SIGN_OUT_URL)
                        .header("Authorization", "Bearer not.a.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signOut_expiredToken_returns401() throws Exception {
        String token = buildExpiredToken();

        mockMvc.perform(post(SIGN_OUT_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signOut_wrongKeyToken_returns401() throws Exception {
        String token = buildWrongKeyToken();

        mockMvc.perform(post(SIGN_OUT_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signOut_wrongIssuerToken_returns401() throws Exception {
        String token = buildWrongIssuerToken();

        mockMvc.perform(post(SIGN_OUT_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signIn_allFailureCases_returnIdenticalBody() throws Exception {
        var account = userAccountRepository.findByLoginId("ctrl-sup").orElseThrow();
        account.disable();
        userAccountRepository.saveAndFlush(account);

        String unknownBody = mockMvc.perform(post(SIGN_IN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"nobody\",\"password\":\"Password1!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String disabledBody = mockMvc.perform(post(SIGN_IN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"ctrl-sup\",\"password\":\"Password1!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPassBody = mockMvc.perform(post(SIGN_IN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"ctrl-pax\",\"password\":\"WrongPass!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(unknownBody, disabledBody);
        assertEquals(unknownBody, wrongPassBody);
    }

    private String signInAndGetToken(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post(SIGN_IN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"Password1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String buildExpiredToken() throws Exception {
        SecretKey key = new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Instant past = Instant.now().minus(Duration.ofHours(1));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("safebus")
                .subject("999")
                .issueTime(Date.from(past.minus(Duration.ofHours(13))))
                .expirationTime(Date.from(past))
                .claim("role", "PASSENGER")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }

    private String buildWrongIssuerToken() throws Exception {
        SecretKey key = new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("wrong-issuer")
                .subject("999")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(12))))
                .claim("role", "PASSENGER")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }

    private String buildWrongKeyToken() throws Exception {
        String wrongSecret = "wrong-key-for-testing-purposes-only-at-least-32b";
        SecretKey key = new SecretKeySpec(wrongSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("safebus")
                .subject("999")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(12))))
                .claim("role", "PASSENGER")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }
}
