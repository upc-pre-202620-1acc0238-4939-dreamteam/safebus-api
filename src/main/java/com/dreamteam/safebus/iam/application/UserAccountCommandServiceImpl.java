package com.dreamteam.safebus.iam.application;

import com.dreamteam.safebus.iam.domain.model.UserAccount;
import com.dreamteam.safebus.iam.domain.model.UserRole;
import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Service
@Transactional
public class UserAccountCommandServiceImpl implements UserAccountCommandService {

    private static final int MIN_PASSWORD_BYTES = 8;
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserAccountCommandServiceImpl(UserAccountRepository repository,
                                         PasswordEncoder passwordEncoder,
                                         Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public Long createSupervisor(String loginId, String rawPassword, Long companyId) {
        return create(loginId, rawPassword, UserRole.SUPERVISOR, companyId);
    }

    @Override
    public Long createDriver(String loginId, String rawPassword, Long companyId) {
        return create(loginId, rawPassword, UserRole.DRIVER, companyId);
    }

    @Override
    public Long createPassenger(String loginId, String rawPassword) {
        return create(loginId, rawPassword, UserRole.PASSENGER, null);
    }

    private Long create(String loginId, String rawPassword, UserRole role, Long companyId) {
        validatePasswordLength(rawPassword);
        if (repository.existsByLoginId(loginId.trim())) {
            throw new ConflictException("LOGIN_ID_TAKEN", "loginId already in use");
        }
        String hash = passwordEncoder.encode(rawPassword);
        UserAccount account = UserAccount.create(loginId, hash, role, companyId, clock);
        return repository.save(account).getId();
    }

    private void validatePasswordLength(String rawPassword) {
        int byteLength = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MIN_PASSWORD_BYTES) {
            throw new RuleViolationException("PASSWORD_TOO_SHORT",
                    "Password must be at least " + MIN_PASSWORD_BYTES + " bytes");
        }
        if (byteLength > MAX_PASSWORD_BYTES) {
            throw new RuleViolationException("PASSWORD_TOO_LONG",
                    "Password must not exceed " + MAX_PASSWORD_BYTES + " bytes");
        }
    }
}
