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
        List<Order> allOrders = orderRepository.findAll();
        List<ReconstructionJob> allJobs = reconstructionJobRepository.findAll();
        long totalScans = scanRepository.count();
        long totalCarts = cartRepository.count();

        List<Order> paidOrders = allOrders.stream()
                .filter(o -> PAID_STATUSES.contains(o.getStatus()))
                .toList();

        BigDecimal totalRevenue = paidOrders.stream()
                .map(Order::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long paidOrderCount = paidOrders.size();
        BigDecimal aov = paidOrderCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(paidOrderCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long digitalOrders = paidOrders.stream()
                .filter(o -> "DIGITAL".equalsIgnoreCase(o.getFulfillmentType()))
                .count();

        long physicalOrders = paidOrders.stream()
                .filter(o -> "PHYSICAL".equalsIgnoreCase(o.getFulfillmentType()))
                .count();

        long previewJobs = allJobs.stream()
                .filter(j -> ReconstructionTier.PREVIEW.name().equalsIgnoreCase(j.getTier().name()))
                .count();

        long successfulPreviews = allJobs.stream()
                .filter(j -> ReconstructionTier.PREVIEW.name().equalsIgnoreCase(j.getTier().name())
                        && j.getStatus() == ReconstructionJobStatus.done)
                .count();

        long premiumJobs = allJobs.stream()
                .filter(j -> ReconstructionTier.HIGH_QUALITY.name().equalsIgnoreCase(j.getTier().name()))
                .count();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalScans", totalScans);
        metrics.put("totalCarts", totalCarts);
        metrics.put("totalOrders", allOrders.size());
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
        List<ReconstructionJob> allJobs = reconstructionJobRepository.findAll();
        List<Order> allOrders = orderRepository.findAll();

        Set<UUID> previewUserIds = allJobs.stream()
                .filter(j -> ReconstructionTier.PREVIEW.name().equalsIgnoreCase(j.getTier().name()))
                .map(j -> j.getScan().getUser().getId())
                .collect(Collectors.toSet());

        Set<UUID> paidUserIds = allOrders.stream()
                .filter(o -> PAID_STATUSES.contains(o.getStatus()))
                .map(Order::getUserId)
                .collect(Collectors.toSet());

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
