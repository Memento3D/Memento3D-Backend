package com.memento3d.products.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ProductNotFoundException extends BusinessException {
    public ProductNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", message);
    }
}
