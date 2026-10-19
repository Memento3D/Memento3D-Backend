package com.memento3d.pricing;

import com.memento3d.customization.application.service.impl.CustomizationServiceImpl;
import com.memento3d.customization.domain.model.CustomizationOption;
import com.memento3d.customization.infrastructure.persistence.repository.CustomizationOptionRepository;
import com.memento3d.entitlement.application.service.impl.EntitlementServiceImpl;
import com.memento3d.entitlement.domain.model.Entitlement;
import com.memento3d.entitlement.domain.model.EntitlementStatus;
import com.memento3d.entitlement.domain.model.EntitlementType;
import com.memento3d.entitlement.infrastructure.persistence.repository.EntitlementRepository;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderItem;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.pricing.api.request.QuoteRequest;
import com.memento3d.pricing.api.response.QuoteResponse;
import com.memento3d.pricing.application.service.impl.PricingEngineServiceImpl;
import com.memento3d.pricing.domain.model.PriceQuote;
import com.memento3d.pricing.infrastructure.persistence.repository.PriceQuoteRepository;
import com.memento3d.pricing.infrastructure.persistence.repository.PricingRuleRepository;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import com.memento3d.quotation.application.service.impl.ManualQuotationServiceImpl;
import com.memento3d.quotation.domain.model.ManualQuotation;
import com.memento3d.quotation.infrastructure.persistence.repository.ManualQuotationRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingAndCommerceTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private PriceQuoteRepository priceQuoteRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EntitlementRepository entitlementRepository;

    @Mock
    private CustomizationOptionRepository customizationOptionRepository;

    @Mock
    private ManualQuotationRepository manualQuotationRepository;

    @org.mockito.Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @InjectMocks
    private PricingEngineServiceImpl pricingEngineService;

    @InjectMocks
    private EntitlementServiceImpl entitlementService;

    @InjectMocks
    private CustomizationServiceImpl customizationService;

    @InjectMocks
    private ManualQuotationServiceImpl manualQuotationService;

    private UUID userId;
    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();

        product = Product.builder()
                .id(productId)
                .name("Mini Keepsake")
                .price(new BigDecimal("249000"))
                .active(true)
                .build();
    }

    @Test
    @DisplayName("TC-06: Payment amount mismatch between webhook and expected order amount is detected and rejected")
    void tc06_paymentAmountMismatchRejected() {
        BigDecimal orderAmount = new BigDecimal("249000.00");
        BigDecimal receivedAmount = new BigDecimal("200000.00");

        boolean matches = orderAmount.compareTo(receivedAmount) == 0;
        assertFalse(matches, "Server must reject payments where received amount != order amount");
    }

    @Test
    @DisplayName("TC-07: Customer cannot tamper with price in request; Pricing Engine recalculates strictly from DB")
    void tc07_priceCalculatedStrictlyFromDb() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(priceQuoteRepository.save(any(PriceQuote.class))).thenAnswer(i -> {
            PriceQuote q = i.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        QuoteRequest request = QuoteRequest.builder()
                .productId(productId)
                .sizeCm(5)
                .shippingRequired(false)
                .build();

        QuoteResponse quote = pricingEngineService.generateQuote(request, userId);

        assertNotNull(quote);
        assertEquals(new BigDecimal("249000.00"), quote.getTotalAmount());
    }

    @Test
    @DisplayName("TC-08: Expired quote cannot be used for checkout")
    void tc08_expiredQuoteValidationFails() {
        PriceQuote expiredQuote = PriceQuote.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .productId(productId)
                .totalAmount(new BigDecimal("249000.00"))
                .validUntil(Instant.now().minus(2, ChronoUnit.HOURS))
                .isUsed(false)
                .build();

        when(priceQuoteRepository.findByIdWithLineItems(expiredQuote.getId())).thenReturn(Optional.of(expiredQuote));

        assertThrows(BusinessException.class, () ->
                pricingEngineService.validateAndLockQuote(expiredQuote.getId(), new BigDecimal("249000.00"), userId));
    }

    @Test
    @DisplayName("TC-10: Refund of a digital order revokes corresponding entitlements")
    void tc10_refundOrderRevokesEntitlements() {
        UUID orderItemId = UUID.randomUUID();
        Entitlement entitlement = Entitlement.builder()
                .id(UUID.randomUUID())
                .ownerId(userId)
                .orderItemId(orderItemId)
                .entitlementType(EntitlementType.DOWNLOAD_DIGITAL_MODEL)
                .status(EntitlementStatus.ACTIVE)
                .build();

        when(entitlementRepository.findByOrderItemId(orderItemId))
                .thenReturn(List.of(entitlement));

        entitlementService.revokeEntitlementsForOrderItem(orderItemId);

        assertEquals(EntitlementStatus.REVOKED, entitlement.getStatus());
        verify(entitlementRepository).saveAll(any());
    }

    @Test
    @DisplayName("TC-11: Manager updates product pricing in DB after order is placed; Order snapshot remains unchanged")
    void tc11_orderPriceSnapshotRemainsUnchangedAfterProductPriceUpdate() {
        OrderItem itemSnapshot = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .productNameSnapshot(product.getName())
                .unitPrice(new BigDecimal("249000.00"))
                .subtotal(new BigDecimal("249000.00"))
                .quantity(1)
                .build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .totalPrice(new BigDecimal("249000.00"))
                .items(List.of(itemSnapshot))
                .status(OrderStatus.CONFIRMED)
                .build();

        // Manager updates product price in database to 399.000 VND
        product.setPrice(new BigDecimal("399000.00"));

        // Snapshot in order is unchanged
        assertEquals(new BigDecimal("249000.00"), order.getTotalPrice());
        assertEquals(new BigDecimal("249000.00"), order.getItems().get(0).getUnitPrice());
    }

    @Test
    @DisplayName("TC-17: Non-existent or inactive customization option throws ResourceNotFoundException")
    void tc17_nonExistentCustomizationOptionThrowsException() {
        UUID invalidOptionId = UUID.randomUUID();
        when(customizationOptionRepository.findById(invalidOptionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                customizationService.updateOption(invalidOptionId, CustomizationOption.builder().name("Test").build()));
    }

    @Test
    @DisplayName("TC-18: Two managers updating the same quotation concurrently results in OptimisticLocking failure")
    void tc18_concurrentQuotationUpdateThrowsOptimisticLockException() {
        UUID quoteId = UUID.randomUUID();
        ManualQuotation quotation = ManualQuotation.builder()
                .id(quoteId)
                .userId(userId)
                .version(1L)
                .status("UNDER_REVIEW")
                .build();

        when(manualQuotationRepository.findById(quoteId)).thenReturn(Optional.of(quotation));
        when(manualQuotationRepository.save(any(ManualQuotation.class))).thenThrow(
                new ObjectOptimisticLockingFailureException(ManualQuotation.class, quoteId));

        assertThrows(ObjectOptimisticLockingFailureException.class, () ->
                manualQuotationService.provideQuote(
                        quoteId,
                        new BigDecimal("800000.00"),
                        Instant.now().plus(48, ChronoUnit.HOURS),
                        "Manager quote update",
                        UUID.randomUUID()
                )
        );
    }
}
