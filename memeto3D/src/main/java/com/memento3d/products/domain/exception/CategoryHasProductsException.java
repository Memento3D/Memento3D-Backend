package com.memento3d.products.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CategoryHasProductsException extends BusinessException {
    public CategoryHasProductsException(String message) {
        super(HttpStatus.BAD_REQUEST, "CATEGORY_HAS_PRODUCTS", message);
    }
}
