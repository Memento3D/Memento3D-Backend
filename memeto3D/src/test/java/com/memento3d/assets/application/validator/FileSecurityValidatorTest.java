package com.memento3d.assets.application.validator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileSecurityValidatorTest {

    private final FileSecurityValidator validator = new FileSecurityValidator();

    @Test
    void sanitizeFilenameAcceptsNormalImageFilename() {
        assertEquals("front.jpg", validator.sanitizeFilename("front.jpg"));
    }

    @Test
    void sanitizeFilenameRemovesPathSeparatorsAndNullBytes() {
        assertEquals(
                "._front_.jpg",
                validator.sanitizeFilename("../front\u0000.jpg")
        );
    }
}
