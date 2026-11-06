package com.memento3d.orders.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.orders.api.response.OrderItemResponse;
import com.memento3d.orders.api.response.OrderListResponse;
import com.memento3d.orders.api.response.OrderResponse;
import com.memento3d.orders.application.service.impl.OrderServiceImpl;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderItem;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.domain.model.ProductCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderManagementRedesignTest {
    @Mock private com.memento3d.customization.application.service.PrintDesignService printDesignService;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID customerId;
    private Order sampleOrder;
    private OrderItem item1;
    private OrderItem item2;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();

        ProductCategory category = ProductCategory.builder()
                .id(UUID.randomUUID())
                .name("Standard Keepsake")
                .slug("standard-keepsake")
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Tượng Chân Dung Kỷ Vật 3D")
                .category(category)
                .thumbnailUrl("https://cdn.memento3d.com/products/keepsake-thumb.jpg")
                .price(new BigDecimal("350000"))
                .build();

        item1 = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .productNameSnapshot("Tượng Chân Dung Kỷ Vật 3D")
                .unitPrice(new BigDecimal("350000"))
                .quantity(1)
                .subtotal(new BigDecimal("350000"))
                .materialSnapshot("Resin Marble White")
                .technologySnapshot("SLA")
                .dimensionsSnapshot("15cm")
                .colorSnapshot("Trắng ngà")
                .finishSnapshot("Phủ bóng UV")
                .baseSnapshot("Đế gỗ óc chó tự nhiên")
                .skuSnapshot("SKU-KEEPSAKE-01")
                .categoryNameSnapshot("Standard Keepsake")
                .customizationSnapshot("{\"engraving\":\"Minh & Trang 2026\"}")
                .build();

        item2 = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .productNameSnapshot("Móc Khóa 3D Mini")
                .unitPrice(new BigDecimal("150000"))
                .quantity(2)
                .subtotal(new BigDecimal("300000"))
                .materialSnapshot("PLA Tough")
                .technologySnapshot("FDM")
                .dimensionsSnapshot("5cm")
                .build();

        List<OrderItem> items = new ArrayList<>(List.of(item1, item2));

        sampleOrder = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("ORD-20261025-TEST")
                .userId(customerId)
                .subtotal(new BigDecimal("650000"))
                .shippingFee(new BigDecimal("30000"))
                .totalPrice(new BigDecimal("680000"))
                .currency("VND")
                .status(OrderStatus.PRINTING)
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.PAID)
                .recipientName("Tran Van B")
                .phone("0987654321")
                .shippingAddress("456 Le Loi, District 1, HCMC")
                .shippingCarrier("Giao Hàng Nhanh (GHN Express)")
                .trackingCode("GHN-20261025-TEST")
                .shippingStatus("IN_TRANSIT")
                .internalPartnerName("Xưởng In 3D Thủ Đức Lab")
                .internalCost(new BigDecimal("280000"))
                .internalQcNotes("Đạt tiêu chuẩn dung sai ±0.1mm")
                .items(items)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Test Customer Order List search & filter with pagination")
    void testGetMyOrders_WithSearchAndFilter() {
        when(orderRepository.searchUserOrders(eq(customerId), eq("Tran Van B"), eq(OrderStatus.PRINTING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleOrder)));

        OrderListResponse response = orderService.getMyOrders(customerId, "Tran Van B", OrderStatus.PRINTING, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getOrders().size());
        assertEquals(1, response.getTotal());

        OrderResponse orderRes = response.getOrders().get(0);
        assertEquals("ORD-20261025-TEST", orderRes.getOrderCode());
        assertEquals("Tran Van B", orderRes.getRecipientName());
        assertEquals(OrderStatus.PRINTING, orderRes.getStatus());
        assertEquals(PaymentStatus.PAID, orderRes.getPaymentStatus());
        assertEquals(2, orderRes.getItemCount());
        assertEquals("Tượng Chân Dung Kỷ Vật 3D", orderRes.getRepresentativeProductName());
        assertEquals("https://cdn.memento3d.com/products/keepsake-thumb.jpg", orderRes.getRepresentativeThumbnailUrl());

        // Privacy assertion: Regular customer must NOT see workshop cost or partner name
        assertNull(orderRes.getInternalPartnerName(), "Internal partner name must be masked for customers");
        assertNull(orderRes.getInternalCost(), "Internal cost must be masked for customers");
        assertNull(orderRes.getInternalQcNotes(), "Internal QC notes must be masked for customers");
    }

    @Test
    @DisplayName("Test Manager Order List sees internal partner details")
    void testGetManagerOrders_SeesInternalPartnerDetails() {
        when(orderRepository.searchManagerOrders(eq("TEST"), eq(OrderStatus.PRINTING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleOrder)));

        OrderListResponse response = orderService.getManagerOrders("TEST", OrderStatus.PRINTING, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getOrders().size());

        OrderResponse orderRes = response.getOrders().get(0);
        assertEquals("Xưởng In 3D Thủ Đức Lab", orderRes.getInternalPartnerName());
        assertEquals(new BigDecimal("280000"), orderRes.getInternalCost());
        assertEquals("Đạt tiêu chuẩn dung sai ±0.1mm", orderRes.getInternalQcNotes());
    }

    @Test
    @DisplayName("Test OrderItemResponse extraction of materials, dimensions, and personalization")
    void testOrderItemResponse_Extraction() {
        OrderItemResponse itemRes = OrderItemResponse.fromEntity(item1);

        assertNotNull(itemRes);
        assertEquals("Tượng Chân Dung Kỷ Vật 3D", itemRes.getProductName());
        assertEquals("Tượng Chân Dung Kỷ Vật 3D", itemRes.getProductNameSnapshot());
        assertEquals("https://cdn.memento3d.com/products/keepsake-thumb.jpg", itemRes.getThumbnailUrl());
        assertEquals("https://cdn.memento3d.com/products/keepsake-thumb.jpg", itemRes.getThumbnailSnapshot());
        assertEquals("Resin Marble White", itemRes.getMaterial());
        assertEquals("SLA", itemRes.getTechnology());
        assertEquals("15cm", itemRes.getDimensions());
        assertEquals("Trắng ngà", itemRes.getColor());
        assertEquals("Phủ bóng UV", itemRes.getFinish());
        assertEquals("Đế gỗ óc chó tự nhiên", itemRes.getBase());
        assertEquals("Minh & Trang 2026", itemRes.getPersonalization());
        assertEquals("SKU-KEEPSAKE-01", itemRes.getSku());
        assertEquals("Standard Keepsake", itemRes.getCategoryName());
    }

    @Test
    @DisplayName("Test getOrderStatusCounts aggregates counts by status and ALL")
    void testGetOrderStatusCounts() {
        List<Object[]> mockRows = new ArrayList<>();
        mockRows.add(new Object[]{"PENDING_PAYMENT", 2L});
        mockRows.add(new Object[]{"CONFIRMED", 3L});
        mockRows.add(new Object[]{"PRINTING", 5L});
        mockRows.add(new Object[]{"DELIVERED", 10L});

        when(orderRepository.countOrdersGroupedByStatusForUser(customerId)).thenReturn(mockRows);

        Map<String, Long> counts = orderService.getOrderStatusCounts(customerId, Role.ROLE_USER);

        assertNotNull(counts);
        assertEquals(2L, counts.get("PENDING_PAYMENT"));
        assertEquals(3L, counts.get("CONFIRMED"));
        assertEquals(5L, counts.get("PRINTING"));
        assertEquals(10L, counts.get("DELIVERED"));
        assertEquals(20L, counts.get("ALL"));
    }
}
