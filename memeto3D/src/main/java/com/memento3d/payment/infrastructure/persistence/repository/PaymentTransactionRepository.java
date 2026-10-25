package com.memento3d.payment.infrastructure.persistence.repository;

import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID>, JpaSpecificationExecutor<PaymentTransaction> {
    Optional<PaymentTransaction> findByMerchantTransactionId(String merchantTransactionId);
    Optional<PaymentTransaction> findByRequestId(String requestId);
    Optional<PaymentTransaction> findByIdAndUserId(UUID id, UUID userId);
    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<PaymentTransaction> findFirstByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    long countByStatus(PaymentStatus status);
    long countByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Query(
        value = """
            SELECT pt FROM PaymentTransaction pt WHERE
            (:status IS NULL OR pt.status = :status) AND
            (:paymentMethod IS NULL OR pt.paymentMethod = :paymentMethod)
            """,
        countQuery = """
            SELECT COUNT(pt) FROM PaymentTransaction pt WHERE
            (:status IS NULL OR pt.status = :status) AND
            (:paymentMethod IS NULL OR pt.paymentMethod = :paymentMethod)
            """
    )
    org.springframework.data.domain.Page<PaymentTransaction> findAllWithFilters(
            @org.springframework.data.repository.query.Param("status") PaymentStatus status,
            @org.springframework.data.repository.query.Param("paymentMethod") com.memento3d.payment.domain.model.PaymentMethod paymentMethod,
            org.springframework.data.domain.Pageable pageable
    );
}
