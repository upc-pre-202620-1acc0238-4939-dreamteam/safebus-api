package com.dreamteam.safebus.notification.interfaces.rest;

import com.dreamteam.safebus.notification.application.NotificationSubscription;
import com.dreamteam.safebus.notification.application.OpenNotificationStreamCommand;
import com.dreamteam.safebus.notification.application.OpenNotificationStreamCommandService;
import com.dreamteam.safebus.notification.application.StreamTicket;
import com.dreamteam.safebus.notification.infrastructure.SseEventSink;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Live Notifications")
public class NotificationStreamController {

    private static final long RECONNECT_MILLIS = 3000;

    private final OpenNotificationStreamCommandService service;

    public NotificationStreamController(OpenNotificationStreamCommandService service) {
        this.service = service;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to the live notifications of the supervisor's company",
        description = "Server-Sent Events stream scoped to the company in the token. Events: "
            + "EmergencyActivated (CRITICAL for a driver emergency, HIGH for an approved passenger group) and "
            + "PassengerReviewRequired (priority REVIEW). The event id is the emergency id or the group "
            + "reference shown by GET /api/v1/review-queue. Delivery is best effort and at most once: after a "
            + "reconnection, reconcile with GET /api/v1/review-queue by identifier. Last-Event-ID is ignored "
            + "and nothing is replayed. The stream closes when the token expires or after the maximum "
            + "connection age; comment frames are sent periodically to keep it alive.")
    @ApiResponse(responseCode = "200", description = "Stream opened (text/event-stream)")
    @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token")
    @ApiResponse(responseCode = "403",
        description = "Supervisor role required, or NOTIFICATION_SCOPE_DENIED (no company in the token, or a "
            + "different companyId was requested)")
    @ApiResponse(responseCode = "429",
        description = "SUBSCRIPTION_LIMIT_USER or SUBSCRIPTION_LIMIT_COMPANY: too many open streams")
    public ResponseEntity<SseEmitter> stream(
            @Parameter(description = "Optional; must equal the company of the token")
            @RequestParam(required = false) Long companyId,
            @AuthenticationPrincipal Jwt jwt) {
        StreamTicket ticket = service.authorize(new OpenNotificationStreamCommand(companyId, jwt.getExpiresAt()));
        SseEmitter emitter = new SseEmitter(ticket.lifetime().toMillis());
        NotificationSubscription subscription = service.subscribe(ticket, new SseEventSink(emitter));
        emitter.onCompletion(() -> service.unsubscribe(subscription));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> service.unsubscribe(subscription));
        try {
            emitter.send(SseEmitter.event().reconnectTime(RECONNECT_MILLIS).comment("connected"));
        } catch (IOException ex) {
            service.unsubscribe(subscription);
            throw new UncheckedIOException(ex);
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache().noTransform())
            .header("X-Accel-Buffering", "no")
            .body(emitter);
    }
}
