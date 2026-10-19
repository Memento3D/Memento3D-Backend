package com.memento3d.pricing.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.pricing.api.request.QuoteRequest;
import com.memento3d.pricing.api.response.QuoteResponse;
import com.memento3d.pricing.application.service.PricingEngineService;
import com.memento3d.pricing.domain.model.PriceQuote;
import com.memento3d.pricing.domain.model.PricingRule;
import com.memento3d.pricing.domain.model.QuoteLineItem;
import com.memento3d.pricing.infrastructure.persistence.repository.PriceQuoteRepository;
import com.memento3d.pricing.infrastructure.persistence.repository.PricingRuleRepository;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingEngineServiceImpl implements PricingEngineService {

    private final ProductRepository productRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final ObjectMapper objectMapper;

    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("30000.00");

    @Override
    @Transactional
    public QuoteResponse generateQuote(QuoteRequest request, UUID userId) {
        log.info("Generating price quote for product {} by user {}", request.getProductId(), userId);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getProductId()));

        if (!product.isActive()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PRODUCT_INACTIVE", "Product is currently inactive.");
        }

        BigDecimal basePrice = product.getPrice().setScale(2, RoundingMode.HALF_UP);
        List<QuoteLineItem> lineItems = new ArrayList<>();

        lineItems.add(QuoteLineItem.builder()
                .title(product.getName() + " (Base)")
                .amount(basePrice)
                .lineType("BASE")
                .build());

        BigDecimal customizationTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // Process customization options if any
        if (request.getCustomization() != null && !request.getCustomization().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.getCustomization().entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();

                if ("engraving".equalsIgnoreCase(key) && val != null && !val.toString().isBlank()) {
                    BigDecimal engravingSurcharge = new BigDecimal("50000.00");
                    customizationTotal = customizationTotal.add(engravingSurcharge);
                    lineItems.add(QuoteLineItem.builder()
                            .title("Custom Engraving: " + val)
                            .amount(engravingSurcharge)
                            .lineType("CUSTOMIZATION")
                            .build());
                } else if ("packaging".equalsIgnoreCase(key) && val != null && !val.toString().isBlank()) {
                    BigDecimal packagingSurcharge = new BigDecimal("80000.00");
                    customizationTotal = customizationTotal.add(packagingSurcharge);
                    lineItems.add(QuoteLineItem.builder()
                            .title("Luxury Packaging: " + val)
                            .amount(packagingSurcharge)
                            .lineType("CUSTOMIZATION")
                            .build());
                }
            }
        }

        BigDecimal shippingFee = request.isShippingRequired()
                ? DEFAULT_SHIPPING_FEE
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (shippingFee.compareTo(BigDecimal.ZERO) > 0) {
            lineItems.add(QuoteLineItem.builder()
                    .title("Standard Delivery")
                    .amount(shippingFee)
                    .lineType("SHIPPING")
                    .build());
        }

        BigDecimal totalAmount = basePrice.add(customizationTotal).add(shippingFee).setScale(2, RoundingMode.HALF_UP);

        String configSnapshot = null;
        try {
            configSnapshot = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.warn("Could not serialize quote configuration: {}", e.getMessage());
        }

        PriceQuote quote = PriceQuote.builder()
                .userId(userId)
                .productId(product.getId())
                .baseAmount(basePrice)
                .customizationAmount(customizationTotal)
                .shippingAmount(shippingFee)
                .totalAmount(totalAmount)
                .currency("VND")
                .validUntil(Instant.now().plus(24, ChronoUnit.HOURS))
                .isUsed(false)
                .configurationSnapshot(configSnapshot)
                .build();

        for (QuoteLineItem item : lineItems) {
            quote.addLineItem(item);
        }

        PriceQuote savedQuote = priceQuoteRepository.save(quote);
        return QuoteResponse.fromEntity(savedQuote);
    }

    @Override
    @Transactional(readOnly = true)
    public QuoteResponse getQuoteById(UUID quoteId, UUID currentUserId, Role currentUserRole) {
        PriceQuote quote = priceQuoteRepository.findByIdWithLineItems(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found with ID: " + quoteId));

        boolean isStaff = currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_MANAGER;
        if (!isStaff && !quote.getUserId().equals(currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_QUOTE_ACCESS", "You cannot view another user's quote.");
        }

        return QuoteResponse.fromEntity(quote);
    }

    @Override
    @Transactional
    public PriceQuote validateAndLockQuote(UUID quoteId, BigDecimal expectedAmount, UUID userId) {
        PriceQuote quote = priceQuoteRepository.findByIdWithLineItems(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found with ID: " + quoteId));

        if (!quote.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_QUOTE_ACCESS", "Quote does not belong to you.");
        }

        if (Boolean.TRUE.equals(quote.getIsUsed())) {
            throw new BusinessException(HttpStatus.CONFLICT, "QUOTE_ALREADY_USED", "This quote has already been consumed for checkout.");
        }

        if (quote.getValidUntil().isBefore(Instant.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "QUOTE_EXPIRED", "This quote has expired. Please calculate a fresh quote.");
        }

        if (expectedAmount != null && quote.getTotalAmount().compareTo(expectedAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PRICE_TAMPERING_DETECTED", "Submitted price does not match verified server quote.");
        }

        quote.setIsUsed(true);
        return priceQuoteRepository.save(quote);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PricingRule> getAllRules() {
        return pricingRuleRepository.findAll();
    }

    @Override
    @Transactional
    public PricingRule saveRule(PricingRule rule) {
        return pricingRuleRepository.save(rule);
    }
}
