package com.memento3d.admin.controller;

import com.memento3d.admin.dto.AdminCategoryPerformanceResponse;
import com.memento3d.admin.dto.AdminDashboardSummaryResponse;
import com.memento3d.admin.dto.AdminRevenueTrendResponse;
import com.memento3d.admin.dto.AdminUserGrowthResponse;
import com.memento3d.admin.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Dashboard", description = "Real-time aggregated KPIs, revenue trends, user growth, and catalog performance")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "Get global admin dashboard statistics summary", description = "Aggregates real database counts and sums across users, orders, revenue, catalog, reconstruction, and hardware")
    public ResponseEntity<AdminDashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getDashboardSummary());
    }

    @GetMapping("")
    @Operation(summary = "Get global admin dashboard statistics (default root endpoint)")
    public ResponseEntity<AdminDashboardSummaryResponse> getRootDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboardSummary());
    }

    @GetMapping("/revenue")
    @Operation(summary = "Get revenue trend timeline for charts", description = "Calculates revenue and order count buckets for periods: today, 7d, 30d, 3m, 6m, 1y")
    public ResponseEntity<AdminRevenueTrendResponse> getRevenueTrend(
            @RequestParam(name = "period", defaultValue = "30d") String period
    ) {
        return ResponseEntity.ok(dashboardService.getRevenueTrend(period));
    }

    @GetMapping("/user-growth")
    @Operation(summary = "Get user growth metrics timeline", description = "Returns daily registrations and cumulative total for periods: 7d, 30d, 90d, 1y")
    public ResponseEntity<AdminUserGrowthResponse> getUserGrowth(
            @RequestParam(name = "period", defaultValue = "30d") String period
    ) {
        return ResponseEntity.ok(dashboardService.getUserGrowth(period));
    }

    @GetMapping("/category-performance")
    @Operation(summary = "Get product category order performance", description = "Returns top categories with associated product and order counts")
    public ResponseEntity<AdminCategoryPerformanceResponse> getCategoryPerformance() {
        return ResponseEntity.ok(dashboardService.getCategoryPerformance());
    }
}
