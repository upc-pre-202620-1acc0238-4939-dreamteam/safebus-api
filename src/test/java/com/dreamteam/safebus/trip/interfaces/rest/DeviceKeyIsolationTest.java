package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.shared.application.CountingSourcePrincipal;
import com.dreamteam.safebus.shared.infrastructure.security.DeviceAuthenticationToken;
import com.dreamteam.safebus.trip.support.AbstractOccupancyTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceKeyIsolationTest extends AbstractOccupancyTest {

    private static final Set<String> DEVICE_ROUTES = Set.of(
        "POST /api/v1/occupancy-initializations",
        "POST /api/v1/occupancy-events",
        "POST /api/v1/occupancy-heartbeats");

    private static final Set<String> PUBLIC_ROUTES = Set.of(
        "POST /api/v1/auth/sign-in",
        "POST /api/v1/passengers",
        "POST /api/v1/contact-requests",
        "GET /api/v1/health");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private List<String> protectedRoutes() {
        Set<String> routes = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            Set<HttpMethod> methods = new TreeSet<>(java.util.Comparator.comparing(HttpMethod::name));
            info.getMethodsCondition().getMethods().forEach(m -> methods.add(HttpMethod.valueOf(m.name())));
            if (methods.isEmpty()) {
                methods.add(HttpMethod.GET);
            }
            for (String path : info.getPathPatternsCondition().getPatternValues()) {
                if (!path.startsWith("/api/v1/") || path.startsWith("/api/v1/test-")) {
                    continue;
                }
                for (HttpMethod method : methods) {
                    String route = method.name() + " " + path;
                    if (!DEVICE_ROUTES.contains(route) && !PUBLIC_ROUTES.contains(route)) {
                        routes.add(route);
                    }
                }
            }
        }
        return new ArrayList<>(routes);
    }

    private String concrete(String route) {
        return route.replaceAll("\\{[^/]+}", "1");
    }

    @Test
    void theWalkCoversTheRoutesThatMatter() {
        List<String> routes = protectedRoutes();

        assertTrue(routes.stream().anyMatch(r -> r.contains("/fleet/overview")), routes.toString());
        assertTrue(routes.stream().anyMatch(r -> r.contains("/counting-sources")), routes.toString());
        assertTrue(routes.stream().anyMatch(r -> r.contains("/emergenc")), routes.toString());
        assertTrue(routes.size() > 10, routes.toString());
    }

    @Test
    void theKeyHeaderAloneIsUnauthorizedOnEveryRouteThatIsNotADeviceRoute() throws Exception {
        for (String route : protectedRoutes()) {
            String[] parts = route.split(" ", 2);
            int actual = mockMvc.perform(request(HttpMethod.valueOf(parts[0]), concrete(parts[1]))
                    .header(KEY_HEADER, rawKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andReturn().getResponse().getStatus();
            assertEquals(401, actual, route);
        }
    }

    @Test
    void theKeyHeaderIsIgnoredOnTheDeviceRoutesWithAnotherMethod() throws Exception {
        for (String path : List.of("/api/v1/occupancy-initializations", "/api/v1/occupancy-events",
                "/api/v1/occupancy-heartbeats")) {
            int actual = mockMvc.perform(request(HttpMethod.GET, path).header(KEY_HEADER, rawKey))
                .andReturn().getResponse().getStatus();
            assertEquals(401, actual, "GET " + path);
        }
    }

    @Test
    void aDevicePrincipalIsForbiddenOnEveryRouteThatIsNotADeviceRoute() throws Exception {
        var device = new DeviceAuthenticationToken(new CountingSourcePrincipal(1L, bus.getId(), company.getId()));
        for (String route : protectedRoutes()) {
            String[] parts = route.split(" ", 2);
            mockMvc.perform(request(HttpMethod.valueOf(parts[0]), concrete(parts[1]))
                    .with(authentication(device))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(result -> assertEquals(403, result.getResponse().getStatus(), route));
        }
    }

    @Test
    void aDevicePrincipalCannotListCountingSourcesOfItsOwnBus() throws Exception {
        var device = new DeviceAuthenticationToken(new CountingSourcePrincipal(1L, bus.getId(), company.getId()));
        mockMvc.perform(request(HttpMethod.GET, "/api/v1/buses/" + bus.getId() + "/counting-sources")
                .with(authentication(device)))
            .andExpect(status().isForbidden());
    }
}
