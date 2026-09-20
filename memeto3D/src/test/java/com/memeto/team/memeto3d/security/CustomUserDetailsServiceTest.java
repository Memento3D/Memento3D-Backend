package com.memeto.team.memeto3d.security;

import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("loadUserByUsername - User found returns CustomUserDetails")
    void testLoadUserByUsernameFound() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("hashed_password")
                .fullName("Full Name")
                .role(Role.ROLE_USER)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername("user@example.com");

        assertNotNull(userDetails);
        assertEquals("user@example.com", userDetails.getUsername());
        assertEquals("hashed_password", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        verify(userRepository, times(1)).findByEmail("user@example.com");
    }

    @Test
    @DisplayName("loadUserByUsername - User not found throws UsernameNotFoundException")
    void testLoadUserByUsernameNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername("notfound@example.com"));
        verify(userRepository, times(1)).findByEmail("notfound@example.com");
    }
}
