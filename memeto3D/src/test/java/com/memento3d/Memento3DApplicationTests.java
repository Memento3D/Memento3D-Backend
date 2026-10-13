package com.memento3d;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class Memento3DApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void testMain() {
        try (var mockedSpringApp = org.mockito.Mockito.mockStatic(org.springframework.boot.SpringApplication.class)) {
            mockedSpringApp.when(() -> org.springframework.boot.SpringApplication.run(Memento3DApplication.class, new String[]{}))
                    .thenReturn(null);
            Memento3DApplication.main(new String[]{});
            mockedSpringApp.verify(() -> org.springframework.boot.SpringApplication.run(Memento3DApplication.class, new String[]{}));
        }
    }

    @Test
    void testInstance() {
        org.junit.jupiter.api.Assertions.assertNotNull(new Memento3DApplication());
    }

}
