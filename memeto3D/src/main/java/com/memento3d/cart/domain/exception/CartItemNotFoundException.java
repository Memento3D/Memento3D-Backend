package com.memento3d.cart.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CartItemNotFoundException extends BusinessException {
    public CartItemNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", message);
    }
}
