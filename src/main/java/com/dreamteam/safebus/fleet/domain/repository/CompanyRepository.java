package com.dreamteam.safebus.fleet.domain.repository;

import com.dreamteam.safebus.fleet.domain.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
