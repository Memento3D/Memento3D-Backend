package com.memento3d.identity.api;

import com.memento3d.identity.domain.model.User;

import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.identity.application.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UUID userId;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userResponse = UserResponse.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("John Doe")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    void getUserById_Success() {
        when(userService.getUserById(userId)).thenReturn(userResponse);

        ResponseEntity<UserResponse> response = userController.getUserById(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(userId, response.getBody().getId());
        assertEquals("user@example.com", response.getBody().getEmail());

        verify(userService, times(1)).getUserById(userId);
    }

    @Test
    void getUserById_NotFound() {
        when(userService.getUserById(userId)).thenThrow(new ResourceNotFoundException("User not found with ID: " + userId));

        assertThrows(ResourceNotFoundException.class, () -> userController.getUserById(userId));

        verify(userService, times(1)).getUserById(userId);
    }
}
