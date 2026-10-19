package com.memento3d.quotation.application.service.impl;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.quotation.application.service.ManualQuotationService;
import com.memento3d.quotation.domain.model.ManualQuotation;
import com.memento3d.quotation.infrastructure.persistence.repository.ManualQuotationRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManualQuotationServiceImpl implements ManualQuotationService {

    private final ManualQuotationRepository manualQuotationRepository;
    private final OutboxPublisherService outboxPublisher;

    @Override
    @Transactional
    public ManualQuotation submitRequest(ManualQuotation request, UUID userId) {
        log.info("Customer {} submitted manual quotation request", userId);
        request.setUserId(userId);
        request.setStatus("SUBMITTED");
        ManualQuotation saved = manualQuotationRepository.save(request);

        outboxPublisher.publish(
                "MANUAL_QUOTATION",
                saved.getId().toString(),
                "QUOTATION_SUBMITTED",
                Map.of("id", saved.getId(), "userId", userId)
        );

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public ManualQuotation getById(UUID id, UUID currentUserId, Role currentUserRole) {
        ManualQuotation quote = manualQuotationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manual quotation not found: " + id));

        boolean isStaff = currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_MANAGER;
        if (!isStaff && !quote.getUserId().equals(currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_QUOTATION_ACCESS", "You do not own this quotation.");
        }

        return quote;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualQuotation> getMyQuotations(UUID userId) {
        return manualQuotationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualQuotation> getPendingQuotations() {
        return manualQuotationRepository.findByStatusOrderByCreatedAtDesc("SUBMITTED");
    }

    @Override
    @Transactional
    public ManualQuotation provideQuote(UUID id, BigDecimal quotedAmount, Instant validUntil, String managerNotes, UUID managerId) {
        ManualQuotation quote = manualQuotationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manual quotation not found: " + id));

        if (quotedAmount == null || quotedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_QUOTE_AMOUNT", "Quoted amount must be positive.");
        }

        quote.setQuotedAmount(quotedAmount);
        quote.setAssignedManagerId(managerId);
        quote.setValidUntil(validUntil != null ? validUntil : Instant.now().plus(7, ChronoUnit.DAYS));
        quote.setManagerNotes(managerNotes);
        quote.setStatus("QUOTED");

        ManualQuotation saved = manualQuotationRepository.save(quote);
        log.info("Manager {} provided quote {} for quotation request {}", managerId, quotedAmount, id);

        outboxPublisher.publish(
                "MANUAL_QUOTATION",
                saved.getId().toString(),
                "QUOTATION_PROVIDED",
                Map.of("id", saved.getId(), "userId", saved.getUserId(), "quotedAmount", quotedAmount)
        );

        return saved;
    }

    @Override
    @Transactional
    public ManualQuotation acceptQuote(UUID id, UUID currentUserId) {
        ManualQuotation quote = manualQuotationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manual quotation not found: " + id));

        if (!quote.getUserId().equals(currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_QUOTATION_ACCESS", "You do not own this quotation.");
        }

        if (!"QUOTED".equalsIgnoreCase(quote.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_QUOTATION_STATUS", "Quotation is not in QUOTED status.");
        }

        if (quote.getValidUntil() != null && quote.getValidUntil().isBefore(Instant.now())) {
            quote.setStatus("EXPIRED");
            manualQuotationRepository.save(quote);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "QUOTATION_EXPIRED", "This quotation has expired.");
        }

        quote.setStatus("ACCEPTED");
        return manualQuotationRepository.save(quote);
    }

    @Override
    @Transactional
    public ManualQuotation rejectQuote(UUID id, UUID currentUserId) {
        ManualQuotation quote = manualQuotationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manual quotation not found: " + id));

        if (!quote.getUserId().equals(currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_QUOTATION_ACCESS", "You do not own this quotation.");
        }

        quote.setStatus("REJECTED");
        return manualQuotationRepository.save(quote);
    }
}
