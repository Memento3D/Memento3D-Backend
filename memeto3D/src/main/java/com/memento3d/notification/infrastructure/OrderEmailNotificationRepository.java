package com.memento3d.notification.infrastructure;

import com.memento3d.notification.domain.model.OrderEmailNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderEmailNotificationRepository extends JpaRepository<OrderEmailNotification, UUID> {

    Optional<OrderEmailNotification> findByOrderIdAndEmailType(UUID orderId, String emailType);

    boolean existsByOrderIdAndEmailTypeAndStatus(UUID orderId, String emailType, String status);

    List<OrderEmailNotification> findByStatusAndRetryCountLessThan(String status, int maxRetries, Pageable pageable);
}
