package com.memento3d.admin.service;

import com.memento3d.admin.dto.AdminCreateUserRequest;
import com.memento3d.admin.dto.AdminUpdateUserRequest;
import com.memento3d.admin.dto.AdminUserDetailResponse;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminUserService {
    Page<UserResponse> listUsers(String search, Role role, UserStatus status, AuthProvider provider, Pageable pageable);
    AdminUserDetailResponse getUserDetail(UUID id);
    UserResponse createUser(AdminCreateUserRequest request, UUID adminActorId);
    UserResponse updateUser(UUID id, AdminUpdateUserRequest request, UUID adminActorId);
    UserResponse lockUser(UUID id, UUID adminActorId);
    UserResponse unlockUser(UUID id, UUID adminActorId);
    UserResponse activateUser(UUID id, UUID adminActorId);
    UserResponse deactivateUser(UUID id, UUID adminActorId);
    void deleteUser(UUID id, UUID adminActorId);
    UserResponse updateUserRole(UUID id, Role newRole, UUID adminActorId);
}
