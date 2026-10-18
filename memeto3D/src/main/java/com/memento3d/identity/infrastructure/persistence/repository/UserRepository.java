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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
    Page<User> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(String email, String fullName, Pageable pageable);
    long countByRole(Role role);

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
}
