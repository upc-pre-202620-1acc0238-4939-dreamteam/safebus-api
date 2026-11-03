package com.dreamteam.safebus.safetycase.domain.repository;

import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PassengerRequestRepository extends JpaRepository<PassengerRequest, String> {

    boolean existsByGroupIdAndPassengerUserId(Long groupId, Long passengerUserId);

    long countByGroupIdAndStatus(Long groupId, RequestStatus status);

    List<PassengerRequest> findByGroupIdIn(Collection<Long> groupIds);

    List<PassengerRequest> findByPassengerUserIdOrderByReceivedAtDescIdDesc(Long passengerUserId);
}
