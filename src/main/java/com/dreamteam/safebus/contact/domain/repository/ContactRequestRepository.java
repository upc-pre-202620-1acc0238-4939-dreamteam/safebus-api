package com.dreamteam.safebus.contact.domain.repository;

import com.dreamteam.safebus.contact.domain.model.ContactRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, String> {
}
