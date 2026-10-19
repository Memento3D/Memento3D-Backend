package com.memento3d.admin.service;

import com.memento3d.admin.dto.AdminCategoryPerformanceResponse;
import com.memento3d.admin.dto.AdminDashboardSummaryResponse;
import com.memento3d.admin.dto.AdminRevenueTrendResponse;
import com.memento3d.admin.dto.AdminUserGrowthResponse;

public interface AdminDashboardService {
    AdminDashboardSummaryResponse getDashboardSummary();
    AdminRevenueTrendResponse getRevenueTrend(String period);
    AdminUserGrowthResponse getUserGrowth(String period);
    AdminCategoryPerformanceResponse getCategoryPerformance();
}
