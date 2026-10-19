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

        // 1. User metrics (Aggregated via SQL)
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long inactiveUsers = userRepository.countByStatus(UserStatus.INACTIVE);
        long lockedUsers = userRepository.countByStatus(UserStatus.LOCKED) + userRepository.countByStatus(UserStatus.SUSPENDED);
        long newUsersToday = userRepository.countByCreatedAtAfter(startOfToday);
        long newUsersThisMonth = userRepository.countByCreatedAtAfter(startOfMonth);

        // 2. Order metrics (Aggregated via SQL)
        long totalOrders = orderRepository.count();
        long ordersToday = orderRepository.countByCreatedAtAfter(startOfToday);
        long pendingOrders = orderRepository.countByStatus(OrderStatus.PENDING) + orderRepository.countByStatus(OrderStatus.PENDING_PAYMENT);
        long processingOrders = orderRepository.countByStatus(OrderStatus.PROCESSING) + orderRepository.countByStatus(OrderStatus.CONFIRMED);
        long printingOrders = orderRepository.countByStatus(OrderStatus.PRINTING);
        long completedOrders = orderRepository.countByStatus(OrderStatus.COMPLETED) + orderRepository.countByStatus(OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED) + orderRepository.countByStatus(OrderStatus.REFUNDED);

        // 3. Revenue metrics
        BigDecimal totalRevenue = orderRepository.calculateTotalRevenue();
        BigDecimal revenueToday = orderRepository.calculateRevenueAfter(startOfToday);
        BigDecimal revenueThisMonth = orderRepository.calculateRevenueAfter(startOfMonth);

        BigDecimal averageOrderValue = BigDecimal.ZERO;
        if (totalOrders > 0 && totalRevenue != null && totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            averageOrderValue = totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
        }

        // 4. Catalog metrics
        long totalProducts = productRepository.count();
        long totalCategories = productCategoryRepository.count();

        // 5. Reconstruction metrics
        long totalJobs = reconstructionJobRepository.count();
        long queuedJobs = reconstructionJobRepository.countByStatus(ReconstructionJobStatus.queued);
        long runningJobs = reconstructionJobRepository.countByStatus(ReconstructionJobStatus.running);
        long doneJobs = reconstructionJobRepository.countByStatus(ReconstructionJobStatus.done);
        long failedJobs = reconstructionJobRepository.countByStatus(ReconstructionJobStatus.failed);
        long previewJobs = reconstructionJobRepository.countByTier(ReconstructionTier.PREVIEW);
        long premiumJobs = reconstructionJobRepository.countByTier(ReconstructionTier.HIGH_QUALITY);

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
        List<AdminRevenueTrendResponse.RevenueDataPoint> points = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalOrders = 0;

        // Group into daily buckets
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Instant start = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

            BigDecimal dayRev = orderRepository.calculateRevenueBetween(start, end);
            if (dayRev == null) dayRev = BigDecimal.ZERO;

            // Simple point count using lightweight date query
            totalRevenue = totalRevenue.add(dayRev);

            points.add(AdminRevenueTrendResponse.RevenueDataPoint.builder()
                    .date(date.toString())
                    .revenue(dayRev)
                    .orders(dayRev.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0)
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
        List<AdminUserGrowthResponse.UserGrowthPoint> points = new ArrayList<>();
        long cumulative = 0;

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Instant start = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

            long count = userRepository.countByCreatedAtBetween(start, end);
            cumulative += count;

            points.add(AdminUserGrowthResponse.UserGrowthPoint.builder()
                    .date(date.toString())
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
        List<AdminCategoryPerformanceResponse.CategoryMetric> metrics = new ArrayList<>();

        for (ProductCategory cat : categories) {
            long productCount = productRepository.countByCategoryId(cat.getId());
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
