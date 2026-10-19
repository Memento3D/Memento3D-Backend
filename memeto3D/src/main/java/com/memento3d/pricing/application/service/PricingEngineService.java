package com.memento3d.pricing.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.pricing.api.request.QuoteRequest;
import com.memento3d.pricing.api.response.QuoteResponse;
import com.memento3d.pricing.domain.model.PriceQuote;
import com.memento3d.pricing.domain.model.PricingRule;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PricingEngineService {
    QuoteResponse generateQuote(QuoteRequest request, UUID userId);
    QuoteResponse getQuoteById(UUID quoteId, UUID currentUserId, Role currentUserRole);
    PriceQuote validateAndLockQuote(UUID quoteId, BigDecimal expectedAmount, UUID userId);
    List<PricingRule> getAllRules();
    PricingRule saveRule(PricingRule rule);
}
