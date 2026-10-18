package com.memento3d.cart.application.service;

import com.memento3d.cart.api.request.AddToCartRequest;
import com.memento3d.cart.api.request.UpdateCartItemRequest;
import com.memento3d.cart.api.response.CartResponse;

import java.util.UUID;

public interface CartService {

    CartResponse getCart(UUID customerId);

    CartResponse addToCart(UUID customerId, AddToCartRequest request);

    CartResponse updateCartItem(UUID customerId, UUID cartItemId, UpdateCartItemRequest request);

    CartResponse removeCartItem(UUID customerId, UUID cartItemId);

    void clearCart(UUID customerId);
}
