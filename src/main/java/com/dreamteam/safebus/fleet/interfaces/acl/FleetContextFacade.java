package com.dreamteam.safebus.fleet.interfaces.acl;

import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class FleetContextFacade {

    public record DriverInfo(Long driverId, Long userAccountId, Long companyId,
                             boolean enabled, Instant credentialExpiresAt) {}

    private final DriverRepository driverRepository;

    public FleetContextFacade(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    public Optional<DriverInfo> findDriverByQrCredential(String qrCredential) {
        return driverRepository.findByQrCredential(qrCredential)
            .map(d -> new DriverInfo(d.getId(), d.getUserAccountId(), d.getCompanyId(),
                                     d.isEnabled(), d.getQrCredentialExpiresAt()));
    }
}
