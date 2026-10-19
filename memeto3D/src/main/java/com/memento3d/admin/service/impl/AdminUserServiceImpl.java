package com.memento3d.admin.service.impl;

import com.memento3d.admin.dto.AdminCreateUserRequest;
import com.memento3d.admin.dto.AdminUpdateUserRequest;
import com.memento3d.admin.dto.AdminUserDetailResponse;
import com.memento3d.admin.service.AdminUserService;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.domain.model.UserStatus;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import com.memento3d.shared.application.service.AuditLogService;
import com.memento3d.shared.exception.BadRequestException;
import com.memento3d.shared.exception.DuplicateResourceException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ReconstructionJobRepository reconstructionJobRepository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(
            UserRepository userRepository,
            OrderRepository orderRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            ReconstructionJobRepository reconstructionJobRepository,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.reconstructionJobRepository = reconstructionJobRepository;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String search, Role role, UserStatus status, AuthProvider provider, Pageable pageable) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return userRepository.findAllWithAdvancedFilters(role, status, provider, cleanSearch, pageable)
                .map(UserResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + id));

        // Aggregate statistics directly from databases
        long totalOrders = orderRepository.countByUserId(id);
        long completedOrders = orderRepository.countByUserIdAndStatus(id, com.memento3d.orders.domain.model.OrderStatus.COMPLETED);
        long cancelledOrders = orderRepository.countByUserIdAndStatus(id, com.memento3d.orders.domain.model.OrderStatus.CANCELLED);
        BigDecimal totalSpending = orderRepository.calculateTotalSpendingByUserId(id);
        long reconstructionJobs = reconstructionJobRepository.countByScan_User_Id(id);
        long printJobs = 0; // Handled through orders count

        AdminUserDetailResponse.UserStatistics stats = AdminUserDetailResponse.UserStatistics.builder()
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .totalSpending(totalSpending != null ? totalSpending : BigDecimal.ZERO)
                .reconstructionJobs(reconstructionJobs)
                .printJobs(printJobs)
                .build();

        return AdminUserDetailResponse.fromEntity(user, stats);
    }

    @Override
    @Transactional
    public UserResponse createUser(AdminCreateUserRequest request, UUID adminActorId) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Email đã được đăng ký trong hệ thống: " + normalizedEmail);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .role(request.getRole() != null ? request.getRole() : Role.ROLE_USER)
                .status(request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true) // Admin created accounts are verified by default
                .build();

        User saved = userRepository.save(user);

        log.info("Admin {} created user account {} with role {}", adminActorId, saved.getEmail(), saved.getRole());
        auditLogService.log(adminActorId, "USER_CREATED", "User", saved.getId().toString(), null, "Created user with role: " + saved.getRole());

        return UserResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, AdminUpdateUserRequest request, UUID adminActorId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + id));

        String beforeData = String.format("fullName=%s, phone=%s, role=%s, status=%s",
                user.getFullName(), user.getPhone(), user.getRole(), user.getStatus());

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }

        if (request.getRole() != null) {
            // Safety rule: Admin cannot demote themselves to non-admin
            if (id.equals(adminActorId) && request.getRole() != Role.ROLE_ADMIN) {
                throw new BadRequestException("Quản trị viên không được tự hạ cấp vai trò của chính mình.");
            }
            user.setRole(request.getRole());
        }

        if (request.getStatus() != null) {
            // Safety rule: Admin cannot lock/deactivate themselves
            if (id.equals(adminActorId) && (request.getStatus() == UserStatus.LOCKED || request.getStatus() == UserStatus.INACTIVE || request.getStatus() == UserStatus.DELETED)) {
                throw new BadRequestException("Quản trị viên không được tự khóa hoặc vô hiệu hóa tài khoản của chính mình.");
            }
            user.setStatus(request.getStatus());
        }

        User saved = userRepository.save(user);

        String afterData = String.format("fullName=%s, phone=%s, role=%s, status=%s",
                saved.getFullName(), saved.getPhone(), saved.getRole(), saved.getStatus());

        log.info("Admin {} updated user {}", adminActorId, saved.getEmail());
        auditLogService.log(adminActorId, "USER_UPDATED", "User", saved.getId().toString(), beforeData, afterData);

        return UserResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public UserResponse lockUser(UUID id, UUID adminActorId) {
        if (id.equals(adminActorId)) {
            throw new BadRequestException("Quy tắc an toàn: Quản trị viên không được tự khóa tài khoản của chính mình.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + id));

        UserStatus oldStatus = user.getStatus();
        user.setStatus(UserStatus.LOCKED);
        User saved = userRepository.save(user);

        log.info("Admin {} locked user account {}", adminActorId, user.getEmail());
        auditLogService.log(adminActorId, "USER_LOCKED", "User", user.getId().toString(), oldStatus.name(), UserStatus.LOCKED.name());

        return UserResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public UserResponse unlockUser(UUID id, UUID adminActorId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + id));

        UserStatus oldStatus = user.getStatus();
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);

        log.info("Admin {} unlocked user account {}", adminActorId, user.getEmail());
        auditLogService.log(adminActorId, "USER_UNLOCKED", "User", user.getId().toString(), oldStatus.name(), UserStatus.ACTIVE.name());

        return UserResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public UserResponse activateUser(UUID id, UUID adminActorId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + id));

        UserStatus oldStatus = user.getStatus();
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);

        log.info("Admin {} activated user account {}", adminActorId, user.getEmail());
        auditLogService.log(adminActorId, "USER_ACTIVATED", "User", user.getId().toString(), oldStatus.name(), UserStatus.ACTIVE.name());

        return UserResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public UserResponse deactivateUser(UUID id, UUID adminActorId) {
        if (id.equals(adminActorId)) {
            throw new BadRequestException("Quy tắc an toàn: Quản trị viên không được tự vô hiệu hóa tài khoản của chính mình.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + id));

        UserStatus oldStatus = user.getStatus();
        user.setStatus(UserStatus.INACTIVE);
        User saved = userRepository.save(user);

        log.info("Admin {} deactivated user account {}", adminActorId, user.getEmail());
        auditLogService.log(adminActorId, "USER_DEACTIVATED", "User", user.getId().toString(), oldStatus.name(), UserStatus.INACTIVE.name());

        return UserResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteUser(UUID id, UUID adminActorId) {
        if (id.equals(adminActorId)) {
            throw new BadRequestException("Quy tắc an toàn: Quản trị viên không được xóa tài khoản của chính mình.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + id));

        // Safety Rule 7: Do not hard-delete accounts with linked Orders, Payments, or Jobs
        long ordersCount = orderRepository.countByUserId(id);
        long paymentsCount = paymentTransactionRepository.countByUserId(id);
        long jobsCount = reconstructionJobRepository.countByScan_User_Id(id);

        if (ordersCount > 0 || paymentsCount > 0 || jobsCount > 0) {
            log.warn("Soft deleting user {} due to existing ledger dependencies (orders={}, payments={}, jobs={})",
                    user.getEmail(), ordersCount, paymentsCount, jobsCount);
            user.setStatus(UserStatus.DELETED);
            userRepository.save(user);
            auditLogService.log(adminActorId, "USER_SOFT_DELETED", "User", id.toString(), "ACTIVE", "DELETED");
            return;
        }

        userRepository.delete(user);
        log.info("Admin {} permanently deleted user {}", adminActorId, user.getEmail());
        auditLogService.log(adminActorId, "USER_PERMANENTLY_DELETED", "User", id.toString(), user.getEmail(), null);
    }

    @Override
    @Transactional
    public UserResponse updateUserRole(UUID id, Role newRole, UUID adminActorId) {
        if (id.equals(adminActorId) && newRole != Role.ROLE_ADMIN) {
            throw new BadRequestException("Quản trị viên không thể tự xóa quyền ADMIN của chính mình.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + id));

        Role oldRole = user.getRole();
        user.setRole(newRole);
        User saved = userRepository.save(user);

        log.info("Admin {} changed role for user {} from {} to {}", adminActorId, user.getEmail(), oldRole, newRole);
        auditLogService.log(adminActorId, "ROLE_CHANGED", "User", user.getId().toString(), oldRole.name(), newRole.name());

        return UserResponse.fromEntity(saved);
    }
}
