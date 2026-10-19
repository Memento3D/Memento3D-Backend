package com.memento3d.address.infrastructure.persistence.repository;

import com.memento3d.address.domain.model.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {

    /** Default address first, then newest. Single query, no joins. */
    List<UserAddress> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(UUID userId);

    /** Ownership-scoped lookup: another user's id simply yields empty (-> 404, never leaks existence). */
    Optional<UserAddress> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    Optional<UserAddress> findFirstByUserIdAndIdNotOrderByCreatedAtDesc(UUID userId, UUID excludedId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserAddress a SET a.defaultAddress = false WHERE a.userId = :userId AND a.defaultAddress = true")
    int clearDefault(@Param("userId") UUID userId);

    /**
     * Row lock on the owner so concurrent address mutations of the same user are serialised
     * (keeps "exactly one default" atomic on top of the partial unique index).
     */
    @Query(value = "SELECT id FROM users WHERE id = :userId FOR UPDATE", nativeQuery = true)
    List<Object> lockOwner(@Param("userId") UUID userId);
}
