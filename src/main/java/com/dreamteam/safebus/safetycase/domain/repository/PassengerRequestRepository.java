package com.dreamteam.safebus.safetycase.domain.repository;

import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerRequestRepository extends JpaRepository<PassengerRequest, String> {

    boolean existsByGroupIdAndPassengerUserId(Long groupId, Long passengerUserId);

    long countByGroupIdAndStatus(Long groupId, RequestStatus status);
}
