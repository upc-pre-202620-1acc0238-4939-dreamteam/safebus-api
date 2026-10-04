package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.port.QrCodeGenerator;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class CreateBusCommandServiceImpl implements CreateBusCommandService {

    private final BusRepository busRepository;
    private final QrCodeGenerator qrCodeGenerator;
    private final CurrentUserProvider currentUserProvider;

    public CreateBusCommandServiceImpl(BusRepository busRepository,
                                        QrCodeGenerator qrCodeGenerator,
                                        CurrentUserProvider currentUserProvider) {
        this.busRepository = busRepository;
        this.qrCodeGenerator = qrCodeGenerator;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public Bus create(CreateBusCommand command) {
        var user = currentUserProvider.current();
        String normalized = command.plate() == null ? null
            : command.plate().trim().toUpperCase(Locale.ROOT);
        if (normalized != null && busRepository.existsByPlate(normalized)) {
            throw new ConflictException("PLATE_TAKEN", "plate already in use");
        }
        Bus bus = Bus.create(user.companyId(), command.plate(), qrCodeGenerator);
        try {
            return busRepository.save(bus);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("PLATE_TAKEN", "plate already in use");
        }
    }
}
