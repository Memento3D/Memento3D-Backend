package com.memento3d.quotation.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.quotation.domain.model.ManualQuotation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ManualQuotationService {
    ManualQuotation submitRequest(ManualQuotation request, UUID userId);
    ManualQuotation getById(UUID id, UUID currentUserId, Role currentUserRole);
    List<ManualQuotation> getMyQuotations(UUID userId);
    List<ManualQuotation> getPendingQuotations();
    ManualQuotation provideQuote(UUID id, BigDecimal quotedAmount, Instant validUntil, String managerNotes, UUID managerId);
    ManualQuotation acceptQuote(UUID id, UUID currentUserId);
    ManualQuotation rejectQuote(UUID id, UUID currentUserId);
}
