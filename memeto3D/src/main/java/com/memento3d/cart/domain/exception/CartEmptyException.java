package com.memento3d.cart.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CartEmptyException extends BusinessException {
    public CartEmptyException(String message) {
        super(HttpStatus.BAD_REQUEST, "CART_EMPTY", message);
    }
}
