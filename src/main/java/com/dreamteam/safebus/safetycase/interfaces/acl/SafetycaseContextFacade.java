package com.dreamteam.safebus.safetycase.interfaces.acl;

import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class SafetycaseContextFacade {

    public record OpenEmergencyView(String emergencyId, Long busId, Long shiftId, String source,
                                    String priority, String status, Instant activatedAt,
                                    Instant receivedAt, Instant attentionStartedAt) {}

    private static final List<EmergencyStatus> OPEN_STATUSES =
        List.of(EmergencyStatus.ACTIVE, EmergencyStatus.IN_PROGRESS);

    private final EmergencyRepository emergencyRepository;

    public SafetycaseContextFacade(EmergencyRepository emergencyRepository) {
        this.emergencyRepository = emergencyRepository;
    }

    // Open means ACTIVE or IN_PROGRESS, whatever the state of the shift; newest received first
    @Transactional(readOnly = true)
    public List<OpenEmergencyView> findOpenEmergenciesOfCompany(Long companyId) {
        return emergencyRepository
            .findByCompanyIdAndStatusInOrderByReceivedAtDesc(companyId, OPEN_STATUSES).stream()
            .map(e -> new OpenEmergencyView(e.getId(), e.getBusId(), e.getShiftId(),
                e.getSource().name(), e.getPriority().name(), e.getStatus().name(),
                e.getActivatedAt(), e.getReceivedAt(), e.getAttentionStartedAt()))
            .toList();
    }
}
