package com.dreamteam.safebus.iam.application;

import com.dreamteam.safebus.iam.domain.model.UserAccount;
import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.iam.infrastructure.security.JwtTokenIssuer;
import com.dreamteam.safebus.shared.domain.exceptions.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class SignInCommandServiceImpl implements SignInCommandService {

    private static final String INVALID_CREDENTIALS_CODE = "INVALID_CREDENTIALS";
    private static final String INVALID_CREDENTIALS_MSG = "Invalid credentials";

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenIssuer tokenIssuer;
    private final Clock clock;
    private final String dummyHash;

    public SignInCommandServiceImpl(UserAccountRepository repository,
                                    PasswordEncoder passwordEncoder,
                                    JwtTokenIssuer tokenIssuer,
                                    Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("__dummy_safebus__");
    }

    @Override
    public SignInResult signIn(SignInCommand command) {
        String loginId = command.loginId().trim().toLowerCase(java.util.Locale.ROOT);
        Optional<UserAccount> accountOpt = repository.findByLoginId(loginId);
        String hashToCheck = accountOpt.map(UserAccount::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(command.password(), hashToCheck);

        if (accountOpt.isEmpty() || !passwordMatches || !accountOpt.get().isEnabled()) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_CODE, INVALID_CREDENTIALS_MSG);
        }

        UserAccount account = accountOpt.get();
        String token = tokenIssuer.issue(account.getId(), account.getRole(), account.getCompanyId());
        Instant expiresAt = Instant.now(clock).plus(tokenIssuer.getTtl());
        return new SignInResult(token, account.getRole().name(), expiresAt);
    }
}
