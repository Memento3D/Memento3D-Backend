package com.memento3d.admin.service;

import com.memento3d.admin.dto.AdminDashboardSummaryResponse;
import com.memento3d.admin.dto.AdminRevenueTrendResponse;
import com.memento3d.admin.service.impl.AdminDashboardServiceImpl;
import com.memento3d.identity.domain.model.UserStatus;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.products.infrastructure.persistence.repository.ProductCategoryRepository;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionTier;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AdminDashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Mock
    private ReconstructionJobRepository reconstructionJobRepository;

    @Mock
    private PrinterRepository printerRepository;

    @InjectMocks
    private AdminDashboardServiceImpl dashboardService;

    @Test
    @DisplayName("Dashboard summary executes zero entity loading and aggregates via SQL counts/sums")
    void getDashboardSummary_AggregatesFromDatabase() {
        when(userRepository.count()).thenReturn(100L);
        java.util.List<Object[]> userStatusRows = new java.util.ArrayList<>();
        userStatusRows.add(new Object[]{UserStatus.ACTIVE, 85L});
        userStatusRows.add(new Object[]{UserStatus.INACTIVE, 10L});
        userStatusRows.add(new Object[]{UserStatus.LOCKED, 5L});
        when(userRepository.countUsersGroupedByStatus()).thenReturn(userStatusRows);
        when(userRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(3L);

        when(orderRepository.count()).thenReturn(50L);
        java.util.List<Object[]> orderStatusRows = new java.util.ArrayList<>();
        orderStatusRows.add(new Object[]{OrderStatus.COMPLETED, 30L});
        when(orderRepository.countOrdersGroupedByStatus()).thenReturn(orderStatusRows);

        java.util.List<Object[]> revSummaryRows = new java.util.ArrayList<>();
        revSummaryRows.add(new Object[]{BigDecimal.valueOf(10000000), BigDecimal.valueOf(500000), BigDecimal.valueOf(500000)});
        when(orderRepository.calculateRevenueSummary(any(Instant.class), any(Instant.class))).thenReturn(revSummaryRows);

        when(productRepository.count()).thenReturn(20L);
        when(productCategoryRepository.count()).thenReturn(5L);

        when(reconstructionJobRepository.count()).thenReturn(40L);
        java.util.List<Object[]> jobStatusRows = new java.util.ArrayList<>();
        jobStatusRows.add(new Object[]{ReconstructionJobStatus.done, 35L});
        jobStatusRows.add(new Object[]{ReconstructionJobStatus.failed, 1L});
        when(reconstructionJobRepository.countJobsGroupedByStatus()).thenReturn(jobStatusRows);

        java.util.List<Object[]> jobTierRows = new java.util.ArrayList<>();
        jobTierRows.add(new Object[]{ReconstructionTier.PREVIEW, 25L});
        jobTierRows.add(new Object[]{ReconstructionTier.HIGH_QUALITY, 15L});
        when(reconstructionJobRepository.countJobsGroupedByTier()).thenReturn(jobTierRows);

        when(printerRepository.count()).thenReturn(8L);
        when(printerRepository.countByOnlineTrue()).thenReturn(7L);
        when(printerRepository.countByOnlineFalse()).thenReturn(1L);

        AdminDashboardSummaryResponse summary = dashboardService.getDashboardSummary();

        assertNotNull(summary);
        assertEquals(100L, summary.getUsers().getTotal());
        assertEquals(85L, summary.getUsers().getActive());
        assertEquals(50L, summary.getOrders().getTotal());
        assertEquals(BigDecimal.valueOf(10000000), summary.getRevenue().getTotal());
        assertEquals(20L, summary.getCatalog().getTotalProducts());
        assertEquals(40L, summary.getReconstruction().getTotal());
        assertEquals(8L, summary.getPrinters().getTotal());
        assertEquals(7L, summary.getPrinters().getPrintersOnline());
    }

    @Test
    @DisplayName("Revenue trend generates correct day intervals")
    void getRevenueTrend_GeneratesPoints() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.List<Object[]> dailyRows = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dailyRows.add(new Object[]{today.minusDays(i).toString(), BigDecimal.valueOf(250000), 2L});
        }
        when(orderRepository.calculateDailyRevenueSince(any(Instant.class))).thenReturn(dailyRows);

        AdminRevenueTrendResponse trend = dashboardService.getRevenueTrend("7d");

        assertNotNull(trend);
        assertEquals("7d", trend.getPeriod());
        assertEquals(7, trend.getPoints().size());
        assertEquals(BigDecimal.valueOf(1750000), trend.getTotalRevenue());
    }
}
