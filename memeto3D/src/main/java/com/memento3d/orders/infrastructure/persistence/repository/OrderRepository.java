package com.memento3d.orders.infrastructure.persistence.repository;

import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    Optional<Order> findByOrderCode(String orderCode);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.userId = :userId")
    Optional<Order> findWithItemsByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM Order o WHERE o.orderCode = :orderCode")
    Optional<Order> findWithItemsByOrderCode(@Param("orderCode") String orderCode);

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

    @Query(
        value = """
            SELECT o FROM Order o WHERE
            (:status IS NULL OR o.status = :status) AND
            (CAST(:query AS string) IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
              OR LOWER(o.recipientName) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
              OR LOWER(o.phone) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))
            """,
        countQuery = """
            SELECT COUNT(o) FROM Order o WHERE
            (:status IS NULL OR o.status = :status) AND
            (CAST(:query AS string) IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
              OR LOWER(o.recipientName) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
              OR LOWER(o.phone) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))
            """
    )
    Page<Order> searchManagerOrders(
            @Param("query") String query,
            @Param("status") OrderStatus status,
            Pageable pageable
    );
}
