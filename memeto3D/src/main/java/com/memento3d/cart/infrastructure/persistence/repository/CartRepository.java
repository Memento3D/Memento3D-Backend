package com.memento3d.cart.infrastructure.persistence.repository;

import com.memento3d.cart.domain.model.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByCustomerId(UUID customerId);

    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category"})
    @Query("SELECT c FROM Cart c WHERE c.customerId = :customerId")
    Optional<Cart> findWithItemsByCustomerId(@Param("customerId") UUID customerId);
}
