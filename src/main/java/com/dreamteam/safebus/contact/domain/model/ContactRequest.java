package com.dreamteam.safebus.contact.domain.model;

import com.dreamteam.safebus.contact.domain.port.ReceiptReferenceGenerator;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Entity
public class ContactRequest implements Persistable<String> {

    @Id
    private String id;

    @Transient
    private boolean isNew = true;

    @PostLoad
    void markNotNew() { this.isNew = false; }

    @Override
    public String getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @Column(nullable = false, unique = true, length = 13)
    private String receiptReference;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false, length = 100)
    private String contactName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private boolean consentGiven;

    @Column(nullable = false)
    private Instant receivedAt;

    protected ContactRequest() {}

    public static ContactRequest register(String submissionId, String companyName,
                                          String contactName, String email, boolean consent,
                                          ReceiptReferenceGenerator referenceGenerator, Clock clock) {
        String trimmedCompanyName = validateName(companyName, "companyName", "INVALID_COMPANY_NAME");
        String trimmedContactName = validateName(contactName, "contactName", "INVALID_CONTACT_NAME");
        String trimmedEmail = email == null ? "" : email.trim();
        int at = trimmedEmail.indexOf('@');
        if (trimmedEmail.length() > 254 || at <= 0 || at != trimmedEmail.lastIndexOf('@')
                || trimmedEmail.substring(at + 1).indexOf('.') < 0
                || trimmedEmail.chars().anyMatch(Character::isWhitespace)) {
            throw new RuleViolationException("INVALID_EMAIL", "email must be a valid address of at most 254 characters");
        }
        if (!consent) {
            throw new RuleViolationException("CONSENT_REQUIRED", "consent must be true");
        }

        ContactRequest request = new ContactRequest();
        request.id = submissionId;
        request.receiptReference = referenceGenerator.generate();
        request.companyName = trimmedCompanyName;
        request.contactName = trimmedContactName;
        request.email = trimmedEmail;
        request.consentGiven = true;
        request.receivedAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        return request;
    }

    private static String validateName(String value, String field, String code) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new RuleViolationException(code, field + " must contain 1 to 100 characters");
        }
        return trimmed;
    }

    public boolean hasSamePayloadAs(String companyName, String contactName, String email, boolean consent) {
        return companyName != null && contactName != null && email != null
            && this.companyName.equals(companyName.trim())
            && this.contactName.equals(contactName.trim())
            && this.email.toLowerCase(Locale.ROOT).equals(email.trim().toLowerCase(Locale.ROOT))
            && this.consentGiven == consent;
    }

    public String getReceiptReference() { return receiptReference; }
    public String getCompanyName() { return companyName; }
    public String getContactName() { return contactName; }
    public String getEmail() { return email; }
    public boolean isConsentGiven() { return consentGiven; }
    public Instant getReceivedAt() { return receivedAt; }
}
