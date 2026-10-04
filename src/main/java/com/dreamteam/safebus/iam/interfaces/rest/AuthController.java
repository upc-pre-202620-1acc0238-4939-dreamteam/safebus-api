package com.dreamteam.safebus.iam.interfaces.rest;

import com.dreamteam.safebus.iam.application.SignInCommand;
import com.dreamteam.safebus.iam.application.SignInCommandService;
import com.dreamteam.safebus.iam.application.SignInResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthController {

    private final SignInCommandService signInService;

    public AuthController(SignInCommandService signInService) {
        this.signInService = signInService;
    }

    @Operation(summary = "Sign in", description = "Authenticate with loginId and password. Returns a Bearer JWT.")
    @ApiResponse(responseCode = "200", description = "Authenticated successfully")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @ApiResponse(responseCode = "422", description = "Validation failed")
    @PostMapping("/sign-in")
    public ResponseEntity<SignInResponse> signIn(@Valid @RequestBody SignInRequest request) {
        SignInResult result = signInService.signIn(new SignInCommand(request.loginId(), request.password()));
        return ResponseEntity.ok(new SignInResponse(
                result.accessToken(), "Bearer", result.role(), result.expiresAt()));
    }

    @Operation(summary = "Sign out",
            description = "Invalidates the client session. The JWT is stateless; no server-side state is removed.")
    @ApiResponse(responseCode = "204", description = "Signed out")
    @ApiResponse(responseCode = "401", description = "No valid token provided")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut() {
        return ResponseEntity.noContent().build();
    }
}
