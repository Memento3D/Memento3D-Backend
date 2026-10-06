package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.UserResponse;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.UserRepository;
import com.memeto.team.memeto3d.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCacheService userCacheService;

    @InjectMocks
    private UserServiceImpl userService;

    private UUID userId;
    private User user;
    private UserResponse cachedUserResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("John Doe")
                .role(Role.ROLE_USER)
                .build();

        cachedUserResponse = UserResponse.fromEntity(user);
    }

    @Test
    void getUserById_CacheHit() {
        when(userCacheService.getUserFromCache(userId)).thenReturn(Optional.of(cachedUserResponse));

        UserResponse result = userService.getUserById(userId);

        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("user@example.com", result.getEmail());

        verify(userCacheService, times(1)).getUserFromCache(userId);
        verifyNoInteractions(userRepository);
        verify(userCacheService, never()).cacheUser(any(), any());
    }

    @Test
    void getUserById_CacheMiss_FoundInDatabase() {
        when(userCacheService.getUserFromCache(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse result = userService.getUserById(userId);

        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("user@example.com", result.getEmail());

        verify(userCacheService, times(1)).getUserFromCache(userId);
        verify(userRepository, times(1)).findById(userId);
        verify(userCacheService, times(1)).cacheUser(eq(userId), any(UserResponse.class));
    }

    @Test
    void getUserById_CacheMiss_NotFoundInDatabase_ThrowsException() {
        when(userCacheService.getUserFromCache(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(userId));

        verify(userCacheService, times(1)).getUserFromCache(userId);
        verify(userRepository, times(1)).findById(userId);
        verify(userCacheService, never()).cacheUser(any(), any());
    }
}
