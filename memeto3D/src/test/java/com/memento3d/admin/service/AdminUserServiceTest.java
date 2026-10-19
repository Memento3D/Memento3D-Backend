package com.memento3d.admin.service;

import com.memento3d.admin.dto.AdminCreateUserRequest;
import com.memento3d.admin.dto.AdminUpdateUserRequest;
import com.memento3d.admin.dto.AdminUserDetailResponse;
import com.memento3d.admin.service.impl.AdminUserServiceImpl;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private ReconstructionJobRepository reconstructionJobRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private UUID adminId;
    private UUID targetUserId;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        targetUser = User.builder()
                .id(targetUserId)
                .email("target@memento3d.com")
                .fullName("Target User")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
    }

    @Test
    @DisplayName("Admin creates user successfully with BCrypt encoding and audit log")
    void createUser_Success() {
        AdminCreateUserRequest req = AdminCreateUserRequest.builder()
                .fullName("New Manager")
                .email("manager@memento3d.com")
                .phone("0912345678")
                .password("SecurePass123!")
                .role(Role.ROLE_MANAGER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.existsByEmail("manager@memento3d.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserResponse res = adminUserService.createUser(req, adminId);

        assertNotNull(res);
        assertEquals("manager@memento3d.com", res.getEmail());
        assertEquals(Role.ROLE_MANAGER, res.getRole());
        verify(passwordEncoder).encode("SecurePass123!");
        verify(auditLogService).log(eq(adminId), eq("USER_CREATED"), eq("User"), anyString(), isNull(), anyString());
    }

    @Test
    @DisplayName("Admin creates user with duplicate email throws DuplicateResourceException (409)")
    void createUser_DuplicateEmail_ThrowsConflict() {
        AdminCreateUserRequest req = AdminCreateUserRequest.builder()
                .fullName("Existing")
                .email("existing@memento3d.com")
                .password("Password123")
                .role(Role.ROLE_USER)
                .build();

        when(userRepository.existsByEmail("existing@memento3d.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> adminUserService.createUser(req, adminId));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Admin cannot lock own account (Safety Rule 2)")
    void lockUser_Self_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> adminUserService.lockUser(adminId, adminId));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Admin locks another user account successfully")
    void lockUser_Success() {
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = adminUserService.lockUser(targetUserId, adminId);

        assertEquals(UserStatus.LOCKED, res.getStatus());
        verify(auditLogService).log(eq(adminId), eq("USER_LOCKED"), eq("User"), eq(targetUserId.toString()), eq("ACTIVE"), eq("LOCKED"));
    }

    @Test
    @DisplayName("Admin unlocks user account successfully")
    void unlockUser_Success() {
        targetUser.setStatus(UserStatus.LOCKED);
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = adminUserService.unlockUser(targetUserId, adminId);

        assertEquals(UserStatus.ACTIVE, res.getStatus());
        verify(auditLogService).log(eq(adminId), eq("USER_UNLOCKED"), eq("User"), eq(targetUserId.toString()), eq("LOCKED"), eq("ACTIVE"));
    }

    @Test
    @DisplayName("Admin cannot deactivate own account (Safety Rule 3)")
    void deactivateUser_Self_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> adminUserService.deactivateUser(adminId, adminId));
    }

    @Test
    @DisplayName("Admin cannot delete own account (Safety Rule 1)")
    void deleteUser_Self_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> adminUserService.deleteUser(adminId, adminId));
    }

    @Test
    @DisplayName("Admin deletes user with order dependencies -> soft deletes to DELETED status (Safety Rule 7)")
    void deleteUser_WithOrders_SoftDeletes() {
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(orderRepository.countByUserId(targetUserId)).thenReturn(2L);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        adminUserService.deleteUser(targetUserId, adminId);

        assertEquals(UserStatus.DELETED, targetUser.getStatus());
        verify(userRepository, never()).delete(any(User.class));
        verify(auditLogService).log(eq(adminId), eq("USER_SOFT_DELETED"), eq("User"), eq(targetUserId.toString()), eq("ACTIVE"), eq("DELETED"));
    }

    @Test
    @DisplayName("Admin changes user role successfully with audit log")
    void updateUserRole_Success() {
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = adminUserService.updateUserRole(targetUserId, Role.ROLE_OPERATOR, adminId);

        assertEquals(Role.ROLE_OPERATOR, res.getRole());
        verify(auditLogService).log(eq(adminId), eq("ROLE_CHANGED"), eq("User"), eq(targetUserId.toString()), eq("ROLE_USER"), eq("ROLE_OPERATOR"));
    }

    @Test
    @DisplayName("Admin cannot demote self from ROLE_ADMIN")
    void updateUserRole_SelfDemote_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> adminUserService.updateUserRole(adminId, Role.ROLE_USER, adminId));
    }

    @Test
    @DisplayName("Get user detail includes aggregate order, spending, and job metrics")
    void getUserDetail_IncludesAggregates() {
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(orderRepository.countByUserId(targetUserId)).thenReturn(5L);
        when(orderRepository.countByUserIdAndStatus(targetUserId, com.memento3d.orders.domain.model.OrderStatus.COMPLETED)).thenReturn(4L);
        when(orderRepository.calculateTotalSpendingByUserId(targetUserId)).thenReturn(BigDecimal.valueOf(1500000));
        when(reconstructionJobRepository.countByScan_User_Id(targetUserId)).thenReturn(3L);

        AdminUserDetailResponse detail = adminUserService.getUserDetail(targetUserId);

        assertNotNull(detail);
        assertEquals(targetUserId, detail.getId());
        assertEquals(5L, detail.getStatistics().getTotalOrders());
        assertEquals(4L, detail.getStatistics().getCompletedOrders());
        assertEquals(BigDecimal.valueOf(1500000), detail.getStatistics().getTotalSpending());
        assertEquals(3L, detail.getStatistics().getReconstructionJobs());
    }
}
