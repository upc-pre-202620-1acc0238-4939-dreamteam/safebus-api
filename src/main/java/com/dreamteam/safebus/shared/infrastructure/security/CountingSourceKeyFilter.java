package com.dreamteam.safebus.shared.infrastructure.security;

import com.dreamteam.safebus.shared.application.CountingSourceKeyAuthenticator;
import com.dreamteam.safebus.shared.application.CountingSourcePrincipal;
import com.dreamteam.safebus.shared.domain.CountingSourceAccess;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

// Not a bean on purpose: SecurityConfig adds it to the chain, so it never runs outside it
public class CountingSourceKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Counting-Source-Key";

    private final CountingSourceKeyAuthenticator authenticator;

    public CountingSourceKeyFilter(CountingSourceKeyAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || !DeviceRoutes.matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<CountingSourcePrincipal> principal =
            key.isBlank() ? Optional.empty() : authenticator.authenticate(key);
        if (principal.isEmpty()) {
            SecurityContextHolder.clearContext();
            writeDenied(request, response);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new DeviceAuthenticationToken(principal.get()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private void writeDenied(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
            + "\"detail\":\"" + CountingSourceAccess.DENIED_MESSAGE + "\","
            + "\"instance\":\"" + escape(request.getRequestURI()) + "\","
            + "\"code\":\"" + CountingSourceAccess.DENIED_CODE + "\"}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
