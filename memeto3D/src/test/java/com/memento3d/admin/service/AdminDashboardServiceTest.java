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
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(85L);
        when(userRepository.countByStatus(UserStatus.INACTIVE)).thenReturn(10L);
        when(userRepository.countByStatus(UserStatus.LOCKED)).thenReturn(5L);
        when(userRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(3L);

        when(orderRepository.count()).thenReturn(50L);
        when(orderRepository.countByStatus(OrderStatus.COMPLETED)).thenReturn(30L);
        when(orderRepository.calculateTotalRevenue()).thenReturn(BigDecimal.valueOf(10000000));
        when(orderRepository.calculateRevenueAfter(any(Instant.class))).thenReturn(BigDecimal.valueOf(500000));

        when(productRepository.count()).thenReturn(20L);
        when(productCategoryRepository.count()).thenReturn(5L);

        when(reconstructionJobRepository.count()).thenReturn(40L);
        when(reconstructionJobRepository.countByStatus(ReconstructionJobStatus.done)).thenReturn(35L);
        when(reconstructionJobRepository.countByStatus(ReconstructionJobStatus.failed)).thenReturn(1L);
        when(reconstructionJobRepository.countByTier(ReconstructionTier.PREVIEW)).thenReturn(25L);
        when(reconstructionJobRepository.countByTier(ReconstructionTier.HIGH_QUALITY)).thenReturn(15L);

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
        when(orderRepository.calculateRevenueBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.valueOf(250000));

        AdminRevenueTrendResponse trend = dashboardService.getRevenueTrend("7d");

        assertNotNull(trend);
        assertEquals("7d", trend.getPeriod());
        assertEquals(7, trend.getPoints().size());
        assertEquals(BigDecimal.valueOf(1750000), trend.getTotalRevenue());
    }
}
