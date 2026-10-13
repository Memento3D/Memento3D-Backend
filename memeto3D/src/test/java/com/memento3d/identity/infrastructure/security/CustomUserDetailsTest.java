package com.memento3d.identity.infrastructure.security;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomUserDetailsTest {

    @Test
    @DisplayName("Test CustomUserDetails getters and UserDetails contract methods")
    void testCustomUserDetails() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .password("encodedPassword")
                .fullName("Test FullName")
                .role(Role.ROLE_ADMIN)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(user);

        assertEquals(userId, userDetails.getId());
        assertEquals("test@example.com", userDetails.getEmail());
        assertEquals("encodedPassword", userDetails.getPassword());
        assertEquals("test@example.com", userDetails.getUsername());
        assertEquals("Test FullName", userDetails.getFullName());
        assertEquals(Role.ROLE_ADMIN, userDetails.getRole());

        assertNotNull(userDetails.getAuthorities());
        assertEquals(1, userDetails.getAuthorities().size());
        assertEquals("ROLE_ADMIN", userDetails.getAuthorities().iterator().next().getAuthority());

        assertTrue(userDetails.isAccountNonExpired());
        assertTrue(userDetails.isAccountNonLocked());
        assertTrue(userDetails.isCredentialsNonExpired());
        assertTrue(userDetails.isEnabled());
    }
}
