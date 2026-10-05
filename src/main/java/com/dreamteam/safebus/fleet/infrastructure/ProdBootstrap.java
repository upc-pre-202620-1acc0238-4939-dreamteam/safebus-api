package com.dreamteam.safebus.fleet.infrastructure;

import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.iam.interfaces.acl.IamContextFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("prod")
public class ProdBootstrap implements ApplicationRunner {

    private final CompanyRepository companyRepository;
    private final IamContextFacade iam;
    private final String bootstrapCompany;
    private final String bootstrapSupervisorLogin;
    private final String bootstrapSupervisorPassword;

    public ProdBootstrap(
            CompanyRepository companyRepository,
            IamContextFacade iam,
            @Value("${SAFEBUS_BOOTSTRAP_COMPANY:#{null}}") String bootstrapCompany,
            @Value("${SAFEBUS_BOOTSTRAP_SUPERVISOR_LOGIN:#{null}}") String bootstrapSupervisorLogin,
            @Value("${SAFEBUS_BOOTSTRAP_SUPERVISOR_PASSWORD:#{null}}") String bootstrapSupervisorPassword) {
        this.companyRepository = companyRepository;
        this.iam = iam;
        this.bootstrapCompany = bootstrapCompany;
        this.bootstrapSupervisorLogin = bootstrapSupervisorLogin;
        this.bootstrapSupervisorPassword = bootstrapSupervisorPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (companyRepository.count() > 0) {
            return;
        }

        List<String> missing = new ArrayList<>();
        if (bootstrapCompany == null || bootstrapCompany.isBlank()) {
            missing.add("SAFEBUS_BOOTSTRAP_COMPANY");
        }
        if (bootstrapSupervisorLogin == null || bootstrapSupervisorLogin.isBlank()) {
            missing.add("SAFEBUS_BOOTSTRAP_SUPERVISOR_LOGIN");
        }
        if (bootstrapSupervisorPassword == null || bootstrapSupervisorPassword.isBlank()) {
            missing.add("SAFEBUS_BOOTSTRAP_SUPERVISOR_PASSWORD");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing required bootstrap environment variables: " + String.join(", ", missing));
        }

        Company company = companyRepository.save(Company.create(bootstrapCompany));
        iam.createSupervisorAccount(bootstrapSupervisorLogin, bootstrapSupervisorPassword, company.getId());
    }
}
