package com.memento3d.cart.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.cart.api.request.AddToCartRequest;
import com.memento3d.cart.api.request.UpdateCartItemRequest;
import com.memento3d.cart.api.response.CartItemResponse;
import com.memento3d.cart.api.response.CartResponse;
import com.memento3d.cart.application.service.CartService;
import com.memento3d.cart.domain.exception.CartItemNotFoundException;
import com.memento3d.cart.domain.model.Cart;
import com.memento3d.cart.domain.model.CartItem;
import com.memento3d.cart.infrastructure.persistence.repository.CartItemRepository;
import com.memento3d.cart.infrastructure.persistence.repository.CartRepository;
import com.memento3d.products.domain.exception.ProductInactiveException;
import com.memento3d.products.domain.exception.ProductNotFoundException;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public CartResponse getCart(UUID customerId) {
        Cart cart = getOrCreateCart(customerId);
        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addToCart(UUID customerId, AddToCartRequest request) {
        if (request.getQuantity() < 1) {
            request.setQuantity(1);
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + request.getProductId()));

        if (!product.isPurchasable()) {
            throw new ProductInactiveException("Product '" + product.getName() + "' is currently inactive or out of stock");
        }

        Cart cart = getOrCreateCart(customerId);

        String customizationJson = null;
        if (request.getCustomization() != null && !request.getCustomization().isEmpty()) {
            try {
                customizationJson = objectMapper.writeValueAsString(request.getCustomization());
            } catch (Exception e) {
                log.warn("Could not serialize customization data: {}", e.getMessage());
            }
        }

        final String finalCustJson = customizationJson;

        // Check if same product with same customization is already in cart
        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .filter(item -> {
                    if (item.getCustomizationData() == null && finalCustJson == null) return true;
                    if (item.getCustomizationData() != null && item.getCustomizationData().equals(finalCustJson)) return true;
                    return false;
                })
                .findFirst();

        if (existingItemOpt.isPresent()) {
            CartItem existing = existingItemOpt.get();
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            // Always update to live product price
            existing.setUnitPrice(product.getPrice());
            cartItemRepository.save(existing);
            log.info("Updated quantity of product ID={} in cart ID={}", product.getId(), cart.getId());
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .unitPrice(product.getPrice())
                    .customizationData(customizationJson)
                    .build();
            cart.addItem(newItem);
            cartItemRepository.save(newItem);
            log.info("Added product ID={} to cart ID={}", product.getId(), cart.getId());
        }

        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(UUID customerId, UUID cartItemId, UpdateCartItemRequest request) {
        if (request.getQuantity() < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        Cart cart = getOrCreateCart(customerId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found with ID: " + cartItemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new CartItemNotFoundException("Cart item does not belong to the user's cart");
        }

        item.setQuantity(request.getQuantity());
        // Always refresh price from product
        if (item.getProduct() != null) {
            item.setUnitPrice(item.getProduct().getPrice());
        }
        cartItemRepository.save(item);

        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeCartItem(UUID customerId, UUID cartItemId) {
        Cart cart = getOrCreateCart(customerId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found with ID: " + cartItemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new CartItemNotFoundException("Cart item does not belong to the user's cart");
        }

        cart.removeItem(item);
        cartItemRepository.delete(item);

        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public void clearCart(UUID customerId) {
        cartRepository.findByCustomerId(customerId).ifPresent(cart -> {
            cart.clearItems();
            cartRepository.save(cart);
            log.info("Cleared cart for customer ID={}", customerId);
        });
    }

    private Cart getOrCreateCart(UUID customerId) {
        return cartRepository.findWithItemsByCustomerId(customerId)
                .or(() -> cartRepository.findByCustomerId(customerId))
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .customerId(customerId)
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(CartItemResponse::fromEntity)
                .toList();

        BigDecimal subtotal = itemResponses.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal shippingFee = BigDecimal.ZERO; // Free shipping standard
        BigDecimal total = subtotal.subtract(discount).add(shippingFee);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        int totalItems = cart.getItems().stream().mapToInt(CartItem::getQuantity).sum();

        return CartResponse.builder()
                .items(itemResponses)
                .subtotal(subtotal)
                .discount(discount)
                .shippingFee(shippingFee)
                .total(total)
                .totalItems(totalItems)
                .build();
    }
}
