package com.dreamteam.safebus.iam.application;

public interface UserAccountCommandService {
    Long createSupervisor(String loginId, String rawPassword, Long companyId);
    Long createDriver(String loginId, String rawPassword, Long companyId);
    Long createPassenger(String loginId, String rawPassword);
}
