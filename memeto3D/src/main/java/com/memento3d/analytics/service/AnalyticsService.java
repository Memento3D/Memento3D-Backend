package com.memento3d.analytics.service;

import java.math.BigDecimal;
import java.util.Map;

public interface AnalyticsService {

    Map<String, Object> getDashboardMetrics();

    Map<String, Object> getConversionMetrics();
}
