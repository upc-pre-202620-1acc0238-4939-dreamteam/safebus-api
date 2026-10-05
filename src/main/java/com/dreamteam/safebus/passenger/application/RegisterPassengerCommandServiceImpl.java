package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class RegisterPassengerCommandServiceImpl implements RegisterPassengerCommandService {

    private final RegisterPassengerWriter writer;

    public RegisterPassengerCommandServiceImpl(RegisterPassengerWriter writer) {
        this.writer = writer;
    }

    @Override
    public RegisterPassengerResult register(RegisterPassengerCommand command) {
        try {
            return writer.write(command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            try {
                return writer.write(command);
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException retry) {
                throw new ConflictException("DNI_ALREADY_REGISTERED",
                    "a passenger account with this DNI already exists");
            }
        }
    }
}
