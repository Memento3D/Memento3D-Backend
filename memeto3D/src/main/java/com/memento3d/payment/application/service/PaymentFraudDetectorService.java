package com.memento3d.payment.application.service;

import com.memento3d.operations.reviewqueue.application.service.ManualReviewService;
import com.memento3d.operations.reviewqueue.domain.model.ReviewPriority;
import com.memento3d.operations.reviewqueue.domain.model.ReviewType;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Enterprise Rule-based Payment Fraud & Anomaly Detection Engine.
 * Implements BR-PAY-003, BR-PAY-004: automated velocity check, abnormal order volume,
 * and high-risk threshold escalation to Manual Review Queue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFraudDetectorService {

    private final PaymentTransactionRepository transactionRepository;
    private final ManualReviewService manualReviewService;

    // High order value threshold requiring review if other signals present: 10,000,000 VND
    private static final BigDecimal HIGH_ORDER_THRESHOLD = new BigDecimal("10000000.00");

    public record FraudRiskAssessment(
            int riskScore,
            String riskLevel, // LOW, MEDIUM, HIGH
            boolean requiresManualReview,
            String reasonCode,
            String explanation
    ) {}

    /**
     * Evaluates payment transaction risk score (0 to 100).
     *
     * @param order       Order being paid
     * @param userId      Customer UUID
     * @param paymentTxId Payment Transaction UUID
     * @return risk assessment result
     */
    @Transactional
    public FraudRiskAssessment assessTransactionRisk(Order order, UUID userId, UUID paymentTxId) {
        int riskScore = 0;
        StringBuilder reasons = new StringBuilder();

        // 1. Velocity check: number of recent failed attempts in the last 15 minutes
        Instant recentWindow = Instant.now().minus(15, ChronoUnit.MINUTES);
        long failedCount = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(tx -> tx.getCreatedAt().isAfter(recentWindow))
                .filter(tx -> tx.getStatus() == PaymentStatus.FAILED || tx.getStatus() == PaymentStatus.CANCELLED)
                .count();

        if (failedCount >= 5) {
            riskScore += 45;
            reasons.append("Phát hiện > 5 giao dịch thất bại trong 15 phút. ");
        } else if (failedCount >= 3) {
            riskScore += 25;
            reasons.append("Phát hiện 3-4 giao dịch thất bại gần đây. ");
        }

        // 2. High order value anomaly
        if (order.getTotalPrice() != null && order.getTotalPrice().compareTo(HIGH_ORDER_THRESHOLD) > 0) {
            riskScore += 30;
            reasons.append(String.format("Giá trị đơn hàng cao bất thường (%s VND). ", order.getTotalPrice()));
        }

        // 3. Repeated rapid checkout attempts for same order
        long attemptsForOrder = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(tx -> tx.getOrderId().equals(order.getId()))
                .count();

        if (attemptsForOrder > 4) {
            riskScore += 20;
            reasons.append("Nhiều nỗ lực thanh toán lặp lại cho cùng 1 đơn hàng. ");
        }

        riskScore = Math.min(100, riskScore);

        String riskLevel;
        boolean requiresReview;
        String reasonCode;

        if (riskScore >= 70) {
            riskLevel = "HIGH";
            requiresReview = true;
            reasonCode = "HIGH_FRAUD_RISK_SUSPECTED";

            log.warn("High payment risk detected (score: {}) for order {} by user {}", riskScore, order.getId(), userId);

            // Enqueue into Manual Review Queue
            manualReviewService.enqueueReview(
                    ReviewType.FRAUD,
                    ReviewPriority.HIGH,
                    paymentTxId,
                    order.getOrderCode(),
                    userId,
                    BigDecimal.valueOf(100 - riskScore).divide(BigDecimal.valueOf(100)),
                    reasonCode,
                    reasons.toString(),
                    "VERIFY_CUSTOMER_ID_AND_PAYMENT_SOURCE"
            );

        } else if (riskScore >= 40) {
            riskLevel = "MEDIUM";
            requiresReview = false;
            reasonCode = "ELEVATED_TRANSACTION_VELOCITY";
        } else {
            riskLevel = "LOW";
            requiresReview = false;
            reasonCode = "TRANSACTION_CLEAN";
        }

        return new FraudRiskAssessment(riskScore, riskLevel, requiresReview, reasonCode, reasons.toString());
    }
}
