package com.memento3d.products.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ProductInactiveException extends BusinessException {
    public ProductInactiveException(String message) {
        super(HttpStatus.BAD_REQUEST, "PRODUCT_INACTIVE", message);
    }
}
