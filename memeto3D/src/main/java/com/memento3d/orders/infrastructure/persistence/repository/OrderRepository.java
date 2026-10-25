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

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.userId = :userId")
    Optional<Order> findWithItemsByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"items", "items.product"})
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
    long countByStatus(OrderStatus status);
    long countByCreatedAtAfter(java.time.Instant after);
    long countByUserId(UUID userId);
    long countByUserIdAndStatus(UUID userId, OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.userId = :userId AND o.status != 'CANCELLED'")
    java.math.BigDecimal calculateTotalSpendingByUserId(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status != 'CANCELLED'")
    java.math.BigDecimal calculateTotalRevenue();

    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status != 'CANCELLED' AND o.createdAt >= :after")
    java.math.BigDecimal calculateRevenueAfter(@Param("after") java.time.Instant after);

    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status != 'CANCELLED' AND o.createdAt BETWEEN :start AND :end")
    java.math.BigDecimal calculateRevenueBetween(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countOrdersGroupedByStatus();

    @Query("SELECT o.paymentMethod, COUNT(o) FROM Order o WHERE o.paymentMethod IS NOT NULL GROUP BY o.paymentMethod")
    List<Object[]> countOrdersGroupedByPaymentMethod();

    @Query("SELECT CAST(o.createdAt AS date), COALESCE(SUM(o.totalPrice), 0), COUNT(o) FROM Order o WHERE o.status != 'CANCELLED' AND o.status != 'REFUNDED' AND o.createdAt >= :since GROUP BY CAST(o.createdAt AS date) ORDER BY CAST(o.createdAt AS date) ASC")
    List<Object[]> calculateDailyRevenueSince(@Param("since") java.time.Instant since);

    @Query("SELECT COALESCE(SUM(o.totalPrice), 0), " +
           "COALESCE(SUM(CASE WHEN o.createdAt >= :today THEN o.totalPrice ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN o.createdAt >= :monthStart THEN o.totalPrice ELSE 0 END), 0) " +
           "FROM Order o WHERE o.status != 'CANCELLED' AND o.status != 'REFUNDED'")
    List<Object[]> calculateRevenueSummary(@Param("today") java.time.Instant today, @Param("monthStart") java.time.Instant monthStart);

    @Query("SELECT DISTINCT o.userId FROM Order o WHERE o.status IN :statuses")
    java.util.Set<UUID> findDistinctUserIdsByStatusIn(@Param("statuses") java.util.Collection<OrderStatus> statuses);

    @Query("SELECT o.fulfillmentType, COUNT(o) FROM Order o WHERE o.status IN :statuses GROUP BY o.fulfillmentType")
    List<Object[]> countFulfillmentTypesGroupedByStatusIn(@Param("statuses") java.util.Collection<OrderStatus> statuses);

    @Query("SELECT COUNT(o), COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status IN :statuses")
    List<Object[]> calculatePaidOrderCountAndRevenue(@Param("statuses") java.util.Collection<OrderStatus> statuses);

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

    @Query(
        value = """
            SELECT o FROM Order o WHERE
            (:status IS NULL OR o.status = :status) AND
            (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) AND
            (:userId IS NULL OR o.userId = :userId) AND
            (CAST(:query AS string) IS NULL OR
             LOWER(o.orderCode) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) OR
             LOWER(o.recipientName) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) OR
             (o.phone IS NOT NULL AND LOWER(o.phone) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))))
            """,
        countQuery = """
            SELECT COUNT(o) FROM Order o WHERE
            (:status IS NULL OR o.status = :status) AND
            (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) AND
            (:userId IS NULL OR o.userId = :userId) AND
            (CAST(:query AS string) IS NULL OR
             LOWER(o.orderCode) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) OR
             LOWER(o.recipientName) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) OR
             (o.phone IS NOT NULL AND LOWER(o.phone) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))))
            """
    )
    Page<Order> searchAdminOrders(
            @Param("query") String query,
            @Param("status") OrderStatus status,
            @Param("paymentStatus") com.memento3d.payment.domain.model.PaymentStatus paymentStatus,
            @Param("userId") UUID userId,
            Pageable pageable
    );
}
