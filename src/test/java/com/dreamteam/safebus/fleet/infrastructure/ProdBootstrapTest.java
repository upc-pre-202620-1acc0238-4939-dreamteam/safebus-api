package com.dreamteam.safebus.fleet.infrastructure;

import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.iam.interfaces.acl.IamContextFacade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProdBootstrapTest {

    private static final String COMPANY = "SafeBus Production";
    private static final String LOGIN = "admin";
    private static final String PASSWORD = "Safebus2024!";

    private ProdBootstrap bootstrap(long companyCount, String company, String login, String password) {
        CompanyRepository repo = mock(CompanyRepository.class);
        IamContextFacade iam = mock(IamContextFacade.class);
        when(repo.count()).thenReturn(companyCount);
        Company saved = mock(Company.class);
        when(saved.getId()).thenReturn(1L);
        when(repo.save(any())).thenReturn(saved);
        return new ProdBootstrap(repo, iam, company, login, password);
    }

    @Test
    void run_emptyDb_createsCompanyAndSupervisor() throws Exception {
        CompanyRepository repo = mock(CompanyRepository.class);
        IamContextFacade iam = mock(IamContextFacade.class);
        when(repo.count()).thenReturn(0L);
        Company saved = mock(Company.class);
        when(saved.getId()).thenReturn(42L);
        when(repo.save(any())).thenReturn(saved);

        ProdBootstrap pb = new ProdBootstrap(repo, iam, COMPANY, LOGIN, PASSWORD);
        pb.run(null);

        verify(repo, times(1)).save(any());
        verify(iam, times(1)).createSupervisorAccount(eq(LOGIN), eq(PASSWORD), eq(42L));
    }

    @Test
    void run_companyExists_doesNothing() throws Exception {
        CompanyRepository repo = mock(CompanyRepository.class);
        IamContextFacade iam = mock(IamContextFacade.class);
        when(repo.count()).thenReturn(1L);

        ProdBootstrap pb = new ProdBootstrap(repo, iam, COMPANY, LOGIN, PASSWORD);
        pb.run(null);

        verify(repo, never()).save(any());
        verify(iam, never()).createSupervisorAccount(anyString(), anyString(), anyLong());
    }

    @Test
    void run_missingCompanyVar_throwsWithVarName() {
        ProdBootstrap pb = bootstrap(0L, null, LOGIN, PASSWORD);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pb.run(null));
        assertTrue(ex.getMessage().contains("SAFEBUS_BOOTSTRAP_COMPANY"),
            "Error must name the missing variable");
    }

    @Test
    void run_allVarsMissing_throwsWithAllNames() {
        ProdBootstrap pb = bootstrap(0L, null, null, null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pb.run(null));
        assertTrue(ex.getMessage().contains("SAFEBUS_BOOTSTRAP_COMPANY"),
            "Error must name SAFEBUS_BOOTSTRAP_COMPANY");
        assertTrue(ex.getMessage().contains("SAFEBUS_BOOTSTRAP_SUPERVISOR_LOGIN"),
            "Error must name SAFEBUS_BOOTSTRAP_SUPERVISOR_LOGIN");
        assertTrue(ex.getMessage().contains("SAFEBUS_BOOTSTRAP_SUPERVISOR_PASSWORD"),
            "Error must name SAFEBUS_BOOTSTRAP_SUPERVISOR_PASSWORD");
    }
}
