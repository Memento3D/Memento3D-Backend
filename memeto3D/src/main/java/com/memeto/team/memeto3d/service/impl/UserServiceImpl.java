package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.UserResponse;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.UserRepository;
import com.memeto.team.memeto3d.service.UserCacheService;
import com.memeto.team.memeto3d.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserCacheService userCacheService;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        log.info("Fetching user details for ID: {}", userId);

        // 1. Try to fetch from Redis Cache
        Optional<UserResponse> cachedUser = userCacheService.getUserFromCache(userId);
        if (cachedUser.isPresent()) {
            log.info("Returning cached UserResponse for userId: {}", userId);
            return cachedUser.get();
        }

        // 2. Cache miss -> query PostgreSQL database (source of truth)
        log.info("User cache miss for userId: {}. Querying PostgreSQL database...", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        UserResponse response = UserResponse.fromEntity(user);

        // 3. Save to Redis Cache
        userCacheService.cacheUser(userId, response);

        return response;
    }
}
