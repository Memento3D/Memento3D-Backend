package com.memento3d.analytics.service;

import com.memento3d.cart.infrastructure.persistence.repository.CartRepository;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionTier;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {

    private final OrderRepository orderRepository;
    private final ReconstructionJobRepository reconstructionJobRepository;
    private final ScanRepository scanRepository;
    private final CartRepository cartRepository;

    private static final Set<OrderStatus> PAID_STATUSES = Set.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.PRINTING,
            OrderStatus.READY,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardMetrics() {
        long totalScans = scanRepository.count();
        long totalCarts = cartRepository.count();
        long totalOrders = orderRepository.count();

        // 1. Order KPIs via single SQL aggregate query
        List<Object[]> paidStats = orderRepository.calculatePaidOrderCountAndRevenue(PAID_STATUSES);
        long paidOrderCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        if (!paidStats.isEmpty() && paidStats.get(0) != null) {
            Object[] row = paidStats.get(0);
            if (row.length >= 1 && row[0] instanceof Number cnt) {
                paidOrderCount = cnt.longValue();
            }
            if (row.length >= 2 && row[1] instanceof BigDecimal rev) {
                totalRevenue = rev;
            }
        }

        BigDecimal aov = paidOrderCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(paidOrderCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 2. Fulfillment breakdown via single GROUP BY query
        long digitalOrders = 0;
        long physicalOrders = 0;
        List<Object[]> fulfillmentRows = orderRepository.countFulfillmentTypesGroupedByStatusIn(PAID_STATUSES);
        for (Object[] row : fulfillmentRows) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] instanceof Number cnt) {
                String type = row[0].toString();
                if ("DIGITAL".equalsIgnoreCase(type)) {
                    digitalOrders += cnt.longValue();
                } else if ("PHYSICAL".equalsIgnoreCase(type)) {
                    physicalOrders += cnt.longValue();
                }
            }
        }

        // 3. Reconstruction job breakdown via single GROUP BY query
        long previewJobs = 0;
        long successfulPreviews = 0;
        long premiumJobs = 0;
        List<Object[]> jobRows = reconstructionJobRepository.countGroupedByTierAndStatus();
        for (Object[] row : jobRows) {
            if (row != null && row.length >= 3 && row[0] != null && row[1] != null && row[2] instanceof Number cnt) {
                ReconstructionTier tier = (ReconstructionTier) row[0];
                ReconstructionJobStatus status = (ReconstructionJobStatus) row[1];
                long count = cnt.longValue();

                if (tier == ReconstructionTier.PREVIEW) {
                    previewJobs += count;
                    if (status == ReconstructionJobStatus.done) {
                        successfulPreviews += count;
                    }
                } else if (tier == ReconstructionTier.HIGH_QUALITY) {
                    premiumJobs += count;
                }
            }
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalScans", totalScans);
        metrics.put("totalCarts", totalCarts);
        metrics.put("totalOrders", totalOrders);
        metrics.put("paidOrdersCount", paidOrderCount);
        metrics.put("totalRevenueVnd", totalRevenue);
        metrics.put("averageOrderValueVnd", aov);
        metrics.put("digitalOrdersCount", digitalOrders);
        metrics.put("physicalOrdersCount", physicalOrders);
        metrics.put("previewJobsTotal", previewJobs);
        metrics.put("previewJobsSuccessful", successfulPreviews);
        metrics.put("premiumJobsTotal", premiumJobs);

        return metrics;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getConversionMetrics() {
        Set<UUID> previewUserIds = reconstructionJobRepository.findDistinctUserIdsByTier(ReconstructionTier.PREVIEW);
        Set<UUID> paidUserIds = orderRepository.findDistinctUserIdsByStatusIn(PAID_STATUSES);

        Set<UUID> convertedUsers = new HashSet<>(previewUserIds);
        convertedUsers.retainAll(paidUserIds);

        double conversionRate = previewUserIds.isEmpty() ? 0.0 :
                (double) convertedUsers.size() / previewUserIds.size() * 100.0;

        Map<String, Object> conversions = new LinkedHashMap<>();
        conversions.put("previewUsersCount", previewUserIds.size());
        conversions.put("convertedPaidUsersCount", convertedUsers.size());
        conversions.put("previewToPaidConversionRatePercent", Math.round(conversionRate * 100.0) / 100.0);

        return conversions;
    }
}
