package com.memento3d.operations.application.service;

import com.memento3d.operations.api.response.RedisHealthResponse;

public interface RedisMonitoringService {
    RedisHealthResponse checkHealth();
}
