package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.iam.interfaces.acl.IamContextFacade;
import com.dreamteam.safebus.passenger.domain.model.PassengerAccount;
import com.dreamteam.safebus.passenger.domain.repository.PassengerAccountRepository;
import com.dreamteam.safebus.shared.application.ImageStoragePort;
import com.dreamteam.safebus.shared.domain.ImageInfo;
import com.dreamteam.safebus.shared.domain.ImagePolicy;
import com.dreamteam.safebus.shared.domain.InvalidImageException;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class RegisterPassengerWriterImpl implements RegisterPassengerWriter {

    private static final String DNI_ALREADY_REGISTERED_MSG =
        "a passenger account with this DNI already exists";

    private final PassengerAccountRepository passengerAccountRepository;
    private final IamContextFacade iamFacade;
    private final ImageStoragePort imageStoragePort;
    private final Clock clock;
    private final String currentTermsVersion;

    public RegisterPassengerWriterImpl(
            PassengerAccountRepository passengerAccountRepository,
            IamContextFacade iamFacade,
            ImageStoragePort imageStoragePort,
            Clock clock,
            @Value("${safebus.terms.current-version}") String currentTermsVersion) {
        this.passengerAccountRepository = passengerAccountRepository;
        this.iamFacade = iamFacade;
        this.imageStoragePort = imageStoragePort;
        this.clock = clock;
        this.currentTermsVersion = currentTermsVersion;
    }

    @Override
    public RegisterPassengerResult write(RegisterPassengerCommand cmd) {
        // Validation order: DNI → termsAccepted → termsVersion → photo → duplicate check → IAM → store
        validateDni(cmd.dni());
        validateTermsAccepted(cmd.termsAccepted());
        validateTermsVersion(cmd.termsVersion());
        ImageInfo imageInfo = validateFacePhoto(cmd.facePhotoBytes());

        String normalizedDni = normalizeDni(cmd.dni());
        if (passengerAccountRepository.existsByDni(normalizedDni)) {
            throw new ConflictException("DNI_ALREADY_REGISTERED", DNI_ALREADY_REGISTERED_MSG);
        }

        Long userAccountId;
        try {
            userAccountId = iamFacade.createPassengerAccount(normalizedDni, cmd.rawPassword());
        } catch (ConflictException e) {
            // LOGIN_ID_TAKEN means the DNI is already an IAM login: same observable effect
            throw new ConflictException("DNI_ALREADY_REGISTERED", DNI_ALREADY_REGISTERED_MSG);
        }

        Long faceImageId = imageStoragePort.store(cmd.facePhotoBytes(), imageInfo.contentType());
        PassengerAccount account = PassengerAccount.register(
                userAccountId, cmd.dni(), faceImageId, cmd.termsVersion(), clock);
        passengerAccountRepository.saveAndFlush(account);
        return new RegisterPassengerResult(account.getId());
    }

    private void validateDni(String rawDni) {
        String normalized = rawDni != null ? rawDni.trim() : null;
        if (normalized == null || !normalized.matches("[0-9]{8}")) {
            throw new RuleViolationException("INVALID_DNI", "dni must be exactly 8 ASCII digits");
        }
    }

    private void validateTermsAccepted(String termsAccepted) {
        if (termsAccepted == null || !termsAccepted.trim().equalsIgnoreCase("true")) {
            throw new RuleViolationException("TERMS_NOT_ACCEPTED", "termsAccepted must be \"true\"");
        }
    }

    private void validateTermsVersion(String termsVersion) {
        if (termsVersion == null || termsVersion.isBlank()
                || !currentTermsVersion.equals(termsVersion.trim())) {
            throw new RuleViolationException("TERMS_VERSION_INVALID",
                "termsVersion must be the current version: " + currentTermsVersion);
        }
    }

    private String normalizeDni(String rawDni) {
        return rawDni != null ? rawDni.trim() : null;
    }

    private ImageInfo validateFacePhoto(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new RuleViolationException("FACE_PHOTO_REQUIRED", "face photo is required");
        }
        try {
            return ImagePolicy.inspect(bytes);
        } catch (InvalidImageException ex) {
            throw switch (ex.getReason()) {
                case TOO_LARGE -> new RuleViolationException("FACE_PHOTO_TOO_LARGE",
                    "face photo exceeds 5 MiB");
                case TOO_MANY_PIXELS -> new RuleViolationException("FACE_PHOTO_INVALID",
                    "face photo has too many pixels");
                default -> new RuleViolationException("FACE_PHOTO_INVALID",
                    "face photo is not a valid JPEG or PNG");
            };
        }
    }
}
