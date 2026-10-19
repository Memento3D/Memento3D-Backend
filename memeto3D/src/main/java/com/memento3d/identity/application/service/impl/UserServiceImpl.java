package com.memento3d.identity.application.service.impl;

import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.User;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.identity.application.service.UserCacheService;
import com.memento3d.identity.application.service.UserService;
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
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.memento3d.shared.application.service.AuditLogService auditLogService;

    private static final com.fasterxml.jackson.databind.ObjectMapper AUDIT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

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

    @Override
    @Transactional
    public UserResponse updateMyProfile(UUID userId, com.memento3d.identity.api.request.UpdateProfileRequest request) {
        log.info("Updating profile for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();

        if (request.getFullName() != null) {
            String normalizedName = request.getFullName().trim().replaceAll("\\s+", " ");
            if (normalizedName.length() < 2) {
                throw new com.memento3d.shared.exception.BusinessException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_FULL_NAME",
                        "Họ tên không được để trống và phải có ít nhất 2 ký tự.");
            }
            if (!normalizedName.equals(user.getFullName())) {
                before.put("fullName", user.getFullName());
                after.put("fullName", normalizedName);
                user.setFullName(normalizedName);
            }
        }

        if (request.getPhone() != null) {
            String phone = com.memento3d.shared.util.PhoneNumbers.normalizeOrNull(request.getPhone());
            if (!java.util.Objects.equals(phone, user.getPhone())) {
                before.put("phone", maskPhone(user.getPhone()));
                after.put("phone", maskPhone(phone));
                user.setPhone(phone);
            }
        }

        if (request.getDateOfBirth() != null) {
            if (request.getDateOfBirth().isBefore(java.time.LocalDate.of(1900, 1, 1))) {
                throw new com.memento3d.shared.exception.BusinessException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_DATE_OF_BIRTH",
                        "Ngày sinh không hợp lệ.");
            }
            if (!request.getDateOfBirth().equals(user.getDateOfBirth())) {
                before.put("dateOfBirth", user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null);
                after.put("dateOfBirth", request.getDateOfBirth().toString());
                user.setDateOfBirth(request.getDateOfBirth());
            }
        }

        if (request.getAvatarUrl() != null) {
            String avatar = request.getAvatarUrl().trim();
            String newAvatar = avatar.isEmpty() ? null : avatar;
            if (!java.util.Objects.equals(newAvatar, user.getAvatarUrl())) {
                before.put("avatarUrl", user.getAvatarUrl() != null ? "set" : null);
                after.put("avatarUrl", newAvatar != null ? "set" : null);
                user.setAvatarUrl(newAvatar);
            }
        }

        User savedUser = before.isEmpty() && after.isEmpty() ? user : userRepository.save(user);
        UserResponse response = UserResponse.fromEntity(savedUser);

        userCacheService.evictUser(userId);
        userCacheService.cacheUser(userId, response);

        if (!after.isEmpty() && auditLogService != null) {
            auditLogService.log(userId, "PROFILE_UPDATED", "USER", userId.toString(), toJson(before), toJson(after));
        }
        return response;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return phone;
        }
        return "*".repeat(phone.length() - 3) + phone.substring(phone.length() - 3);
    }

    private static String toJson(java.util.Map<String, Object> data) {
        try {
            return AUDIT_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data.keySet());
        }
    }


    @Override
    @Transactional(readOnly = true)
    public com.memento3d.identity.api.response.UserListResponse getUsers(String search, int page, int limit) {
        int pageIndex = Math.max(0, page - 1);
        int pageSize = Math.min(100, Math.max(1, limit));
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageIndex, pageSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        );

        org.springframework.data.domain.Page<User> userPage;
        if (search != null && !search.isBlank()) {
            String trimmedSearch = search.trim();
            userPage = userRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(trimmedSearch, trimmedSearch, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        java.util.List<UserResponse> userResponses = userPage.getContent().stream()
                .map(UserResponse::fromEntity)
                .toList();

        return com.memento3d.identity.api.response.UserListResponse.builder()
                .users(userResponses)
                .page(page)
                .limit(limit)
                .total(userPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public UserResponse adminCreateUser(com.memento3d.identity.api.request.AdminCreateUserRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        log.info("Admin creating user with email: {}", email);

        if (userRepository.existsByEmail(email)) {
            throw new com.memento3d.identity.domain.exception.EmailAlreadyExistsException("Email already registered: " + email);
        }

        Long storageQuota = request.getStorageQuotaBytes() != null && request.getStorageQuotaBytes() > 0
                ? request.getStorageQuotaBytes() : 10737418240L;

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(request.getRole() != null ? request.getRole() : com.memento3d.identity.domain.model.Role.ROLE_USER)
                .provider(com.memento3d.identity.domain.model.AuthProvider.LOCAL)
                .storageQuotaBytes(storageQuota)
                .storageUsedBytes(0L)
                .build();

        User savedUser = userRepository.save(user);
        UserResponse response = UserResponse.fromEntity(savedUser);
        userCacheService.cacheUser(savedUser.getId(), response);
        return response;
    }

    @Override
    @Transactional
    public UserResponse adminUpdateUser(UUID userId, com.memento3d.identity.api.request.AdminUpdateUserRequest request) {
        log.info("Admin updating user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getStorageQuotaBytes() != null && request.getStorageQuotaBytes() >= 0) {
            user.setStorageQuotaBytes(request.getStorageQuotaBytes());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }

        User savedUser = userRepository.save(user);
        UserResponse response = UserResponse.fromEntity(savedUser);

        userCacheService.evictUser(userId);
        userCacheService.cacheUser(userId, response);
        return response;
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        log.info("Admin deleting/deactivating user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        userCacheService.evictUser(userId);
        userRepository.delete(user);
    }
}
