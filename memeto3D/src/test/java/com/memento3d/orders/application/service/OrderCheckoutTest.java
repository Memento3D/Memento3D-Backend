package com.memento3d.orders.application.service;

import com.memento3d.cart.domain.exception.CartEmptyException;
import com.memento3d.cart.domain.model.Cart;
import com.memento3d.cart.domain.model.CartItem;
import com.memento3d.cart.infrastructure.persistence.repository.CartItemRepository;
import com.memento3d.cart.infrastructure.persistence.repository.CartRepository;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.orders.api.request.CreateOrderRequest;
import com.memento3d.orders.api.response.OrderResponse;
import com.memento3d.orders.application.service.impl.OrderServiceImpl;
import com.memento3d.orders.domain.exception.OrderAccessDeniedException;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderItemRepository;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.domain.model.ProductAvailability;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID customerId;
    private Product product;
    private Cart cart;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();

        product = Product.builder()
                .id(UUID.randomUUID())
                .name("Standard Keepsake Desk Sculpture")
                .price(new BigDecimal("350000"))
                .active(true)
                .availability(ProductAvailability.AVAILABLE)
                .build();

        cart = Cart.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .items(new ArrayList<>())
                .build();

        CartItem cartItem = CartItem.builder()
                .id(UUID.randomUUID())
                .cart(cart)
                .product(product)
                .quantity(2)
                .unitPrice(new BigDecimal("350000"))
                .customizationData("{\"size\":\"15cm\"}")
                .build();

        cart.addItem(cartItem);
    }

    @Test
    @DisplayName("Should successfully checkout cart, recalculate price strictly from DB and clear cart")
    void testCheckoutSuccess() {
        when(cartRepository.findWithItemsByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .recipientName("Nguyen Van A")
                .shippingAddress("123 Le Loi, D1, HCMC")
                .phone("0901234567")
                .paymentMethod(PaymentMethod.CASH)
                .note("Please deliver afternoon")
                .build();

        OrderResponse response = orderService.createOrder(request, customerId);

        assertNotNull(response);
        assertNotNull(response.getOrderCode());
        assertTrue(response.getOrderCode().startsWith("ORD-"));
        assertEquals(new BigDecimal("700000"), response.getTotalPrice());
        assertEquals(PaymentMethod.CASH, response.getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
        assertEquals(1, response.getItems().size());
        assertEquals("Standard Keepsake Desk Sculpture", response.getItems().get(0).getProductName());

        // Verify cart cleared
        assertTrue(cart.getItems().isEmpty());
        verify(cartRepository).save(cart);

        // Verify pending CASH transaction created
        verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    @DisplayName("Should reject checkout when cart is empty")
    void testCheckoutEmptyCart() {
        Cart emptyCart = Cart.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .items(new ArrayList<>())
                .build();

        when(cartRepository.findWithItemsByCustomerId(customerId)).thenReturn(Optional.of(emptyCart));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .recipientName("Nguyen Van A")
                .shippingAddress("123 Le Loi, D1, HCMC")
                .phone("0901234567")
                .build();

        assertThrows(CartEmptyException.class, () -> orderService.createOrder(request, customerId));
    }

    @Test
    @DisplayName("Customer cannot access another customer's order (IDOR protection)")
    void testCustomerCannotAccessAnotherCustomerOrder() {
        UUID otherCustomerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .userId(otherCustomerId)
                .totalPrice(new BigDecimal("350000"))
                .status(OrderStatus.PENDING)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(OrderAccessDeniedException.class, () ->
                orderService.getOrderDetail(orderId, customerId, Role.ROLE_USER));
    }
}
