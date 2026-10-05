package com.dreamteam.safebus.contact.application;

import com.dreamteam.safebus.contact.domain.model.ContactRequest;
import com.dreamteam.safebus.contact.domain.port.ReceiptReferenceGenerator;
import com.dreamteam.safebus.contact.domain.repository.ContactRequestRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
// READ_COMMITTED avoids stale MySQL snapshots when checking committed submissions.
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ContactRequestWriterImpl implements ContactRequestWriter {

    private final ContactRequestRepository repository;
    private final ReceiptReferenceGenerator referenceGenerator;
    private final Clock clock;

    public ContactRequestWriterImpl(ContactRequestRepository repository,
                                    ReceiptReferenceGenerator referenceGenerator, Clock clock) {
        this.repository = repository;
        this.referenceGenerator = referenceGenerator;
        this.clock = clock;
    }

    @Override
    public RegisterContactRequestResult write(RegisterContactRequestCommand command) {
        var existing = repository.findById(command.submissionId());
        if (existing.isPresent()) {
            ContactRequest stored = existing.get();
            if (stored.hasSamePayloadAs(command.companyName(), command.contactName(), command.email(), command.consent())) {
                return new RegisterContactRequestResult(stored.getReceiptReference(), stored.getReceivedAt(), true);
            }
            throw new ConflictException("SUBMISSION_ID_REUSED", "submissionId has already been used with a different payload");
        }

        ContactRequest request = ContactRequest.register(command.submissionId(), command.companyName(),
            command.contactName(), command.email(), command.consent(), referenceGenerator, clock);
        repository.saveAndFlush(request);
        return new RegisterContactRequestResult(request.getReceiptReference(), request.getReceivedAt(), false);
    }
}
