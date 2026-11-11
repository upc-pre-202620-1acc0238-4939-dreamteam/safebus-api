package com.dreamteam.safebus.shared.interfaces.rest;

import com.dreamteam.safebus.iam.application.UserAccountCommandService;
import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorDispatchIntegrationTest {

    private static final String LOGIN_ID = "error-dispatch-sup";
    private static final String PASSWORD = "Password1!";

    @LocalServerPort
    int port;

    @Autowired
    UserAccountCommandService accountService;

    @Autowired
    UserAccountRepository userAccountRepository;

    private final HttpClient client = HttpClient.newHttpClient();
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        accountService.createSupervisor(LOGIN_ID, PASSWORD, 1L);
        HttpResponse<String> signIn = send("/api/v1/auth/sign-in", "application/json",
                "{\"loginId\":\"" + LOGIN_ID + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertEquals(200, signIn.statusCode());
        token = JsonPath.read(signIn.body(), "$.accessToken");
    }

    @AfterEach
    void tearDown() {
        userAccountRepository.findByLoginId(LOGIN_ID).ifPresent(userAccountRepository::delete);
    }

    @Test
    void malformedJsonBody_withValidToken_returns400() throws Exception {
        HttpResponse<String> response = send("/api/v1/buses", "application/json", "{roto", token);

        assertEquals(400, response.statusCode());
    }

    @Test
    void unsupportedContentType_withValidToken_returns415() throws Exception {
        HttpResponse<String> response = send("/api/v1/buses", "text/plain", "plain text", token);

        assertEquals(415, response.statusCode());
    }

    @Test
    void malformedJsonBody_onPublicEndpointWithoutToken_returns400() throws Exception {
        HttpResponse<String> response = send("/api/v1/auth/sign-in", "application/json", "{roto", null);

        assertEquals(400, response.statusCode());
    }

    @Test
    void unsupportedContentType_onPublicEndpointWithoutToken_returns415() throws Exception {
        HttpResponse<String> response = send("/api/v1/auth/sign-in", "text/plain", "plain text", null);

        assertEquals(415, response.statusCode());
    }

    @Test
    void unsupportedMethod_onPublicEndpointWithoutToken_returns405() throws Exception {
        HttpResponse<String> response = send("/api/v1/health", "application/json", "{}", null);

        assertEquals(405, response.statusCode());
    }

    private HttpResponse<String> send(String path, String contentType, String body, String bearerToken)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
