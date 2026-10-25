package com.memento3d.admin.service.impl;

import com.memento3d.admin.dto.*;
import com.memento3d.admin.service.AdminDashboardService;
import com.memento3d.identity.domain.model.UserStatus;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.products.domain.model.ProductCategory;
import com.memento3d.products.infrastructure.persistence.repository.ProductCategoryRepository;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionTier;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ReconstructionJobRepository reconstructionJobRepository;
    private final PrinterRepository printerRepository;

    public AdminDashboardServiceImpl(
            UserRepository userRepository,
            OrderRepository orderRepository,
            ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            ReconstructionJobRepository reconstructionJobRepository,
            PrinterRepository printerRepository
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.reconstructionJobRepository = reconstructionJobRepository;
        this.printerRepository = printerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getDashboardSummary() {
        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // 1. User metrics: grouped status counts in 1 query + 2 date range count queries
        long totalUsers = userRepository.count();
        List<Object[]> userStatusRows = userRepository.countUsersGroupedByStatus();
        Map<UserStatus, Long> userStatusMap = new HashMap<>();
        for (Object[] row : userStatusRows) {
            if (row != null && row.length >= 2 && row[0] instanceof UserStatus status && row[1] instanceof Number cnt) {
                userStatusMap.put(status, cnt.longValue());
            }
        }
        long activeUsers = userStatusMap.getOrDefault(UserStatus.ACTIVE, 0L);
        long inactiveUsers = userStatusMap.getOrDefault(UserStatus.INACTIVE, 0L);
        long lockedUsers = userStatusMap.getOrDefault(UserStatus.LOCKED, 0L) + userStatusMap.getOrDefault(UserStatus.SUSPENDED, 0L);
        long newUsersToday = userRepository.countByCreatedAtAfter(startOfToday);
        long newUsersThisMonth = userRepository.countByCreatedAtAfter(startOfMonth);

        // 2. Order metrics: grouped status counts in 1 query
        long totalOrders = orderRepository.count();
        long ordersToday = orderRepository.countByCreatedAtAfter(startOfToday);
        List<Object[]> orderStatusRows = orderRepository.countOrdersGroupedByStatus();
        Map<OrderStatus, Long> orderStatusMap = new HashMap<>();
        for (Object[] row : orderStatusRows) {
            if (row != null && row.length >= 2 && row[0] instanceof OrderStatus status && row[1] instanceof Number cnt) {
                orderStatusMap.put(status, cnt.longValue());
            }
        }
        long pendingOrders = orderStatusMap.getOrDefault(OrderStatus.PENDING, 0L) + orderStatusMap.getOrDefault(OrderStatus.PENDING_PAYMENT, 0L);
        long processingOrders = orderStatusMap.getOrDefault(OrderStatus.PROCESSING, 0L) + orderStatusMap.getOrDefault(OrderStatus.CONFIRMED, 0L);
        long printingOrders = orderStatusMap.getOrDefault(OrderStatus.PRINTING, 0L);
        long completedOrders = orderStatusMap.getOrDefault(OrderStatus.COMPLETED, 0L) + orderStatusMap.getOrDefault(OrderStatus.DELIVERED, 0L);
        long cancelledOrders = orderStatusMap.getOrDefault(OrderStatus.CANCELLED, 0L) + orderStatusMap.getOrDefault(OrderStatus.REFUNDED, 0L);

        // 3. Revenue metrics: computed in 1 single summary aggregate query
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal revenueToday = BigDecimal.ZERO;
        BigDecimal revenueThisMonth = BigDecimal.ZERO;
        List<Object[]> revSummary = orderRepository.calculateRevenueSummary(startOfToday, startOfMonth);
        if (!revSummary.isEmpty() && revSummary.get(0) != null) {
            Object[] row = revSummary.get(0);
            if (row.length >= 1 && row[0] instanceof BigDecimal b) totalRevenue = b;
            if (row.length >= 2 && row[1] instanceof BigDecimal b) revenueToday = b;
            if (row.length >= 3 && row[2] instanceof BigDecimal b) revenueThisMonth = b;
        }

        BigDecimal averageOrderValue = BigDecimal.ZERO;
        if (totalOrders > 0 && totalRevenue != null && totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            averageOrderValue = totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
        }

        // 4. Catalog metrics
        long totalProducts = productRepository.count();
        long totalCategories = productCategoryRepository.count();

        // 5. Reconstruction metrics: grouped status and tier in 2 queries
        long totalJobs = reconstructionJobRepository.count();
        List<Object[]> jobStatusRows = reconstructionJobRepository.countJobsGroupedByStatus();
        Map<ReconstructionJobStatus, Long> jobStatusMap = new HashMap<>();
        for (Object[] row : jobStatusRows) {
            if (row != null && row.length >= 2 && row[0] instanceof ReconstructionJobStatus status && row[1] instanceof Number cnt) {
                jobStatusMap.put(status, cnt.longValue());
            }
        }
        long queuedJobs = jobStatusMap.getOrDefault(ReconstructionJobStatus.queued, 0L);
        long runningJobs = jobStatusMap.getOrDefault(ReconstructionJobStatus.running, 0L);
        long doneJobs = jobStatusMap.getOrDefault(ReconstructionJobStatus.done, 0L);
        long failedJobs = jobStatusMap.getOrDefault(ReconstructionJobStatus.failed, 0L);

        List<Object[]> jobTierRows = reconstructionJobRepository.countJobsGroupedByTier();
        Map<ReconstructionTier, Long> jobTierMap = new HashMap<>();
        for (Object[] row : jobTierRows) {
            if (row != null && row.length >= 2 && row[0] instanceof ReconstructionTier tier && row[1] instanceof Number cnt) {
                jobTierMap.put(tier, cnt.longValue());
            }
        }
        long previewJobs = jobTierMap.getOrDefault(ReconstructionTier.PREVIEW, 0L);
        long premiumJobs = jobTierMap.getOrDefault(ReconstructionTier.HIGH_QUALITY, 0L);

        // 6. Printer fleet metrics
        long totalPrinters = printerRepository.count();
        long onlinePrinters = printerRepository.countByOnlineTrue();
        long offlinePrinters = printerRepository.countByOnlineFalse();

        // 7. System health overview
        Map<String, String> systemHealth = new LinkedHashMap<>();
        systemHealth.put("database", "HEALTHY");
        systemHealth.put("reconstructionWorker", failedJobs > 5 ? "DEGRADED" : "HEALTHY");
        systemHealth.put("printerFleet", offlinePrinters > 0 && onlinePrinters == 0 ? "DEGRADED" : "HEALTHY");

        return AdminDashboardSummaryResponse.builder()
                .users(AdminDashboardSummaryResponse.UserStats.builder()
                        .total(totalUsers)
                        .active(activeUsers)
                        .inactive(inactiveUsers)
                        .locked(lockedUsers)
                        .newToday(newUsersToday)
                        .newThisMonth(newUsersThisMonth)
                        .build())
                .orders(AdminDashboardSummaryResponse.OrderStats.builder()
                        .total(totalOrders)
                        .ordersToday(ordersToday)
                        .pending(pendingOrders)
                        .processing(processingOrders)
                        .printing(printingOrders)
                        .completed(completedOrders)
                        .cancelled(cancelledOrders)
                        .build())
                .revenue(AdminDashboardSummaryResponse.RevenueStats.builder()
                        .today(revenueToday != null ? revenueToday : BigDecimal.ZERO)
                        .thisMonth(revenueThisMonth != null ? revenueThisMonth : BigDecimal.ZERO)
                        .total(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                        .averageOrderValue(averageOrderValue)
                        .build())
                .catalog(AdminDashboardSummaryResponse.CatalogStats.builder()
                        .totalProducts(totalProducts)
                        .totalCategories(totalCategories)
                        .build())
                .reconstruction(AdminDashboardSummaryResponse.ReconstructionStats.builder()
                        .total(totalJobs)
                        .queued(queuedJobs)
                        .running(runningJobs)
                        .completed(doneJobs)
                        .failed(failedJobs)
                        .previewJobs(previewJobs)
                        .premiumJobs(premiumJobs)
                        .build())
                .printers(AdminDashboardSummaryResponse.PrinterStats.builder()
                        .total(totalPrinters)
                        .printersOnline(onlinePrinters)
                        .printersOffline(offlinePrinters)
                        .build())
                .systemHealth(systemHealth)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRevenueTrendResponse getRevenueTrend(String period) {
        String cleanPeriod = (period != null && !period.isBlank()) ? period.toLowerCase().trim() : "30d";
        int days = switch (cleanPeriod) {
            case "today" -> 1;
            case "7d" -> 7;
            case "3m" -> 90;
            case "6m" -> 180;
            case "1y" -> 365;
            default -> 30; // 30d default
        };

        LocalDate today = LocalDate.now();
        Instant startDate = today.minusDays(days - 1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // 1 single query replaces 30-365 sequential queries
        List<Object[]> rows = orderRepository.calculateDailyRevenueSince(startDate);
        Map<String, BigDecimal[]> dailyData = new HashMap<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalOrders = 0;

        for (Object[] row : rows) {
            if (row != null && row.length >= 3 && row[0] != null) {
                String dateStr = row[0].toString();
                BigDecimal rev = row[1] instanceof BigDecimal b ? b : (row[1] instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO);
                long ordCount = row[2] instanceof Number n ? n.longValue() : 0L;
                dailyData.put(dateStr, new BigDecimal[]{rev, BigDecimal.valueOf(ordCount)});
            }
        }

        List<AdminRevenueTrendResponse.RevenueDataPoint> points = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.toString();
            BigDecimal[] entry = dailyData.get(dateStr);
            BigDecimal dayRev = entry != null ? entry[0] : BigDecimal.ZERO;
            long dayOrders = entry != null ? entry[1].longValue() : 0L;

            totalRevenue = totalRevenue.add(dayRev);
            totalOrders += dayOrders;

            points.add(AdminRevenueTrendResponse.RevenueDataPoint.builder()
                    .date(dateStr)
                    .revenue(dayRev)
                    .orders((int) dayOrders)
                    .build());
        }

        return AdminRevenueTrendResponse.builder()
                .period(cleanPeriod)
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .points(points)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserGrowthResponse getUserGrowth(String period) {
        String cleanPeriod = (period != null && !period.isBlank()) ? period.toLowerCase().trim() : "30d";
        int days = switch (cleanPeriod) {
            case "7d" -> 7;
            case "90d", "3m" -> 90;
            case "1y" -> 365;
            default -> 30;
        };

        LocalDate today = LocalDate.now();
        Instant startDate = today.minusDays(days - 1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // 1 single query replaces 30-365 sequential queries
        List<Object[]> rows = userRepository.countDailyUserRegistrationsSince(startDate);
        Map<String, Long> dailyRegistrations = new HashMap<>();
        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] instanceof Number cnt) {
                dailyRegistrations.put(row[0].toString(), cnt.longValue());
            }
        }

        List<AdminUserGrowthResponse.UserGrowthPoint> points = new ArrayList<>();
        long cumulative = 0;

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.toString();
            long count = dailyRegistrations.getOrDefault(dateStr, 0L);
            cumulative += count;

            points.add(AdminUserGrowthResponse.UserGrowthPoint.builder()
                    .date(dateStr)
                    .registrations(count)
                    .cumulativeTotal(cumulative)
                    .build());
        }

        return AdminUserGrowthResponse.builder()
                .period(cleanPeriod)
                .points(points)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminCategoryPerformanceResponse getCategoryPerformance() {
        List<ProductCategory> categories = productCategoryRepository.findAllByOrderByDisplayOrderAsc();
        List<Object[]> rawCounts = productRepository.countAllProductsGroupedByCategory();
        Map<UUID, Long> productCountMap = new HashMap<>();
        for (Object[] row : rawCounts) {
            if (row != null && row.length >= 2 && row[0] instanceof UUID catId && row[1] instanceof Number cnt) {
                productCountMap.put(catId, cnt.longValue());
            }
        }

        List<AdminCategoryPerformanceResponse.CategoryMetric> metrics = new ArrayList<>();
        for (ProductCategory cat : categories) {
            long productCount = productCountMap.getOrDefault(cat.getId(), 0L);
            metrics.add(AdminCategoryPerformanceResponse.CategoryMetric.builder()
                    .categoryName(cat.getName())
                    .categorySlug(cat.getSlug())
                    .orderCount(productCount)
                    .totalRevenue(BigDecimal.ZERO)
                    .build());
        }

        return AdminCategoryPerformanceResponse.builder()
                .categories(metrics)
                .build();
    }
}
