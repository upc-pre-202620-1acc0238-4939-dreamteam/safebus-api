package com.dreamteam.safebus.passenger.interfaces.rest;

import com.dreamteam.safebus.passenger.application.RegisterPassengerCommand;
import com.dreamteam.safebus.passenger.application.RegisterPassengerCommandService;
import com.dreamteam.safebus.passenger.application.RegisterPassengerResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/passengers")
@Tag(name = "Passengers")
public class PassengerController {

    private final RegisterPassengerCommandService commandService;

    public PassengerController(RegisterPassengerCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a passenger account")
    @ApiResponse(responseCode = "201", description = "Passenger registered")
    @ApiResponse(responseCode = "409", description = "DNI already registered")
    @ApiResponse(responseCode = "422", description = "Validation error (INVALID_DNI, PASSWORD_REQUIRED, etc.)")
    public PassengerResource register(
            @RequestParam(required = false) String loginId,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String dni,
            @RequestParam(required = false) String termsVersion,
            @RequestPart(required = false) MultipartFile facePhoto) throws IOException {

        byte[] photoBytes = facePhoto != null && !facePhoto.isEmpty() ? facePhoto.getBytes() : null;

        RegisterPassengerResult result = commandService.register(
                new RegisterPassengerCommand(loginId, password, dni, termsVersion, photoBytes));

        return new PassengerResource(result.passengerAccountId());
    }
}
