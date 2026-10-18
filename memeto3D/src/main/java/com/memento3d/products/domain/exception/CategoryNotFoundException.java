package com.memento3d.products.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CategoryNotFoundException extends BusinessException {
    public CategoryNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", message);
    }
}
