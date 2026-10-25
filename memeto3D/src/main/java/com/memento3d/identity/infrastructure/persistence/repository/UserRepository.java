package com.memento3d.identity.infrastructure.persistence.repository;

import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
    Page<User> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(String email, String fullName, Pageable pageable);
    long countByRole(Role role);
    long countByStatus(com.memento3d.identity.domain.model.UserStatus status);
    long countByCreatedAtAfter(java.time.Instant after);
    long countByCreatedAtBetween(java.time.Instant start, java.time.Instant end);

    @Query("SELECT u.status, COUNT(u) FROM User u GROUP BY u.status")
    List<Object[]> countUsersGroupedByStatus();

    @Query("SELECT CAST(u.createdAt AS date), COUNT(u) FROM User u WHERE u.createdAt >= :since GROUP BY CAST(u.createdAt AS date) ORDER BY CAST(u.createdAt AS date) ASC")
    List<Object[]> countDailyUserRegistrationsSince(@Param("since") java.time.Instant since);

    @Query(
        value = """
            SELECT u FROM User u WHERE
            (:role IS NULL OR u.role = :role) AND
            (CAST(:search AS string) IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """,
        countQuery = """
            SELECT COUNT(u) FROM User u WHERE
            (:role IS NULL OR u.role = :role) AND
            (CAST(:search AS string) IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """
    )
    Page<User> findAllWithFilters(@Param("role") Role role, @Param("search") String search, Pageable pageable);

    @Query(
        value = """
            SELECT u FROM User u WHERE
            (:role IS NULL OR u.role = :role) AND
            (:status IS NULL OR u.status = :status) AND
            (:provider IS NULL OR u.provider = :provider) AND
            (CAST(:search AS string) IS NULL OR
             LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
             LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
             (u.phone IS NOT NULL AND LOWER(u.phone) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))))
            """,
        countQuery = """
            SELECT COUNT(u) FROM User u WHERE
            (:role IS NULL OR u.role = :role) AND
            (:status IS NULL OR u.status = :status) AND
            (:provider IS NULL OR u.provider = :provider) AND
            (CAST(:search AS string) IS NULL OR
             LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
             LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
             (u.phone IS NOT NULL AND LOWER(u.phone) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))))
            """
    )
    Page<User> findAllWithAdvancedFilters(
            @Param("role") Role role,
            @Param("status") com.memento3d.identity.domain.model.UserStatus status,
            @Param("provider") AuthProvider provider,
            @Param("search") String search,
            Pageable pageable
    );
}
