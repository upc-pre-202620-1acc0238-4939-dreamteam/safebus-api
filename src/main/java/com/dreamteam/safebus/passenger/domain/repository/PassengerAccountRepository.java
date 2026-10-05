package com.dreamteam.safebus.passenger.domain.repository;

import com.dreamteam.safebus.passenger.domain.model.PassengerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerAccountRepository extends JpaRepository<PassengerAccount, Long> {

    boolean existsByDni(String dni);

    boolean existsByUserAccountId(Long userAccountId);
}
