package com.memento3d.cart.application.service;

import com.memento3d.cart.api.request.AddToCartRequest;
import com.memento3d.cart.api.request.UpdateCartItemRequest;
import com.memento3d.cart.api.response.CartResponse;
import com.memento3d.cart.application.service.impl.CartServiceImpl;
import com.memento3d.cart.domain.model.Cart;
import com.memento3d.cart.domain.model.CartItem;
import com.memento3d.cart.infrastructure.persistence.repository.CartItemRepository;
import com.memento3d.cart.infrastructure.persistence.repository.CartRepository;
import com.memento3d.products.domain.exception.ProductInactiveException;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    private UUID customerId;
    private Product activeProduct;
    private Product inactiveProduct;
    private Cart cart;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();

        activeProduct = Product.builder()
                .id(UUID.randomUUID())
                .name("Mini Keepsake")
                .price(new BigDecimal("150000"))
                .active(true)
                .availability(ProductAvailability.AVAILABLE)
                .build();

        inactiveProduct = Product.builder()
                .id(UUID.randomUUID())
                .name("Sold Out Sculpture")
                .price(new BigDecimal("500000"))
                .active(false)
                .availability(ProductAvailability.OUT_OF_STOCK)
                .build();

        cart = Cart.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .items(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Should add active product to cart and calculate correct subtotal")
    void testAddToCartSuccess() {
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.of(activeProduct));
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(i -> i.getArgument(0));

        AddToCartRequest request = AddToCartRequest.builder()
                .productId(activeProduct.getId())
                .quantity(2)
                .build();

        CartResponse response = cartService.addToCart(customerId, request);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(2, response.getTotalItems());
        assertEquals(new BigDecimal("300000"), response.getSubtotal());
    }

    @Test
    @DisplayName("Should reject adding inactive product to cart")
    void testAddToCartInactiveProduct() {
        when(productRepository.findById(inactiveProduct.getId())).thenReturn(Optional.of(inactiveProduct));

        AddToCartRequest request = AddToCartRequest.builder()
                .productId(inactiveProduct.getId())
                .quantity(1)
                .build();

        assertThrows(ProductInactiveException.class, () -> cartService.addToCart(customerId, request));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update cart item quantity and recalculate subtotal")
    void testUpdateCartItemQuantity() {
        UUID itemId = UUID.randomUUID();
        CartItem item = CartItem.builder()
                .id(itemId)
                .cart(cart)
                .product(activeProduct)
                .quantity(1)
                .unitPrice(activeProduct.getPrice())
                .build();
        cart.addItem(item);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        UpdateCartItemRequest request = UpdateCartItemRequest.builder()
                .quantity(3)
                .build();

        CartResponse response = cartService.updateCartItem(customerId, itemId, request);

        assertEquals(3, response.getTotalItems());
        assertEquals(new BigDecimal("450000"), response.getSubtotal());
    }
}
