package com.dreamteam.safebus.shared.infrastructure.security;

import com.dreamteam.safebus.shared.application.CountingSourcePrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountingSourceKeyFilterTest {

    private static final CountingSourcePrincipal PRINCIPAL = new CountingSourcePrincipal(1L, 2L, 3L);
    private static final String VALID = "sbk_valid";

    private final CountingSourceKeyFilter filter = new CountingSourceKeyFilter(
        key -> VALID.equals(key) ? Optional.of(PRINCIPAL) : Optional.empty());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String uri, String key) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (key != null) {
            request.addHeader(CountingSourceKeyFilter.HEADER, key);
        }
        return request;
    }

    private Authentication authDuringChain(MockHttpServletRequest request, MockHttpServletResponse response)
        throws Exception {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(SecurityContextHolder.getContext().getAuthentication());
            }
        });
        return seen.get();
    }

    @Test
    void validKeyOnADeviceRouteAuthenticatesAsDevice() throws Exception {
        for (String path : DeviceRoutes.PATHS) {
            Authentication auth = authDuringChain(request("POST", path, VALID), new MockHttpServletResponse());

            assertTrue(auth instanceof DeviceAuthenticationToken);
            assertEquals(PRINCIPAL, auth.getPrincipal());
            assertNull(auth.getCredentials());
            assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DEVICE")));
        }
    }

    @Test
    void invalidKeyOnADeviceRouteIsDeniedWithoutReachingTheChain() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = request("POST", "/api/v1/occupancy-events", "wrong");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":\"OCCUPANCY_SOURCE_NOT_AUTHORIZED\""));
        assertTrue(body.contains("\"detail\":\"access denied\""));
        assertFalse(body.contains("wrong"));
    }

    @Test
    void blankKeyIsDenied() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("POST", "/api/v1/occupancy-heartbeats", "  "), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void theKeyIsIgnoredOutsideTheDeviceRoutes() throws Exception {
        Authentication auth = authDuringChain(request("GET", "/api/v1/fleet/overview", VALID),
            new MockHttpServletResponse());

        assertNull(auth);
    }

    @Test
    void anInvalidKeyIsIgnoredOutsideTheDeviceRoutes() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("POST", "/api/v1/passengers", "wrong"), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void aDeviceRouteWithAnotherMethodIsNotADeviceRoute() throws Exception {
        Authentication auth = authDuringChain(request("GET", "/api/v1/occupancy-events", VALID),
            new MockHttpServletResponse());

        assertNull(auth);
    }

    @Test
    void withoutTheHeaderNothingHappens() throws Exception {
        Authentication auth = authDuringChain(request("POST", "/api/v1/occupancy-events", null),
            new MockHttpServletResponse());

        assertNull(auth);
    }
}
