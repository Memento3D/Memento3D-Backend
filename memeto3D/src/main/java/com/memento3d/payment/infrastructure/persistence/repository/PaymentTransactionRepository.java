package com.memento3d.payment.infrastructure.persistence.repository;

import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByMerchantTransactionId(String merchantTransactionId);
    Optional<PaymentTransaction> findByRequestId(String requestId);
    Optional<PaymentTransaction> findByIdAndUserId(UUID id, UUID userId);
    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<PaymentTransaction> findFirstByOrderIdAndStatus(UUID orderId, PaymentStatus status);
}
