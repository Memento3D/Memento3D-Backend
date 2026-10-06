package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.RedisHealthResponse;

public interface RedisMonitoringService {
    RedisHealthResponse checkHealth();
}
