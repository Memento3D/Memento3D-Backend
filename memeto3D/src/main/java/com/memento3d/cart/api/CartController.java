package com.memento3d.cart.api;

import com.memento3d.cart.api.request.AddToCartRequest;
import com.memento3d.cart.api.request.UpdateCartItemRequest;
import com.memento3d.cart.api.response.CartResponse;
import com.memento3d.cart.application.service.CartService;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/cart", "/api/cart"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart", description = "Shopping cart operations for authenticated customers")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("isAuthenticated()")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get current customer's shopping cart")
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(cartService.getCart(userDetails.getId()));
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to cart")
    public ResponseEntity<CartResponse> addToCart(
            @Valid @RequestBody AddToCartRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(cartService.addToCart(userDetails.getId(), request));
    }

    @PatchMapping("/items/{cartItemId}")
    @Operation(summary = "Update cart item quantity")
    public ResponseEntity<CartResponse> updateCartItem(
            @PathVariable UUID cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(cartService.updateCartItem(userDetails.getId(), cartItemId, request));
    }

    @DeleteMapping("/items/{cartItemId}")
    @Operation(summary = "Remove item from cart")
    public ResponseEntity<CartResponse> removeCartItem(
            @PathVariable UUID cartItemId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(cartService.removeCartItem(userDetails.getId(), cartItemId));
    }

    @DeleteMapping
    @Operation(summary = "Clear all items in cart")
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal CustomUserDetails userDetails) {
        cartService.clearCart(userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
