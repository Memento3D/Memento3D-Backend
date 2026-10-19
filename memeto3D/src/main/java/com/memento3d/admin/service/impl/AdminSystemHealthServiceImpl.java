package com.memento3d.admin.service.impl;

import com.memento3d.admin.dto.AdminSystemHealthResponse;
import com.memento3d.admin.service.AdminSystemHealthService;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSystemHealthServiceImpl implements AdminSystemHealthService {

    private final DataSource dataSource;
    private final ReconstructionJobRepository reconstructionJobRepository;
    private final PrinterRepository printerRepository;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Override
    public AdminSystemHealthResponse checkHealth() {
        Map<String, AdminSystemHealthResponse.ServiceStatus> services = new LinkedHashMap<>();
        boolean isAllHealthy = true;

        // 1. PostgreSQL Database Check
        long dbStart = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            long latency = System.currentTimeMillis() - dbStart;
            services.put("PostgreSQL Database", AdminSystemHealthResponse.ServiceStatus.builder()
                    .status(valid ? "HEALTHY" : "DEGRADED")
                    .latencyMs(latency)
                    .message(valid ? "Đã kết nối cơ sở dữ liệu PostgreSQL (HikariCP)" : "Kết nối database phản hồi chậm")
                    .build());
            if (!valid) isAllHealthy = false;
        } catch (Exception e) {
            log.error("Database health check failed: {}", e.getMessage());
            services.put("PostgreSQL Database", AdminSystemHealthResponse.ServiceStatus.builder()
                    .status("DOWN")
                    .message("Không thể kết nối cơ sở dữ liệu: " + e.getMessage())
                    .build());
            isAllHealthy = false;
        }

        // 2. Redis Cache Check
        if (redisConnectionFactory != null) {
            long redisStart = System.currentTimeMillis();
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                String ping = connection.ping();
                long latency = System.currentTimeMillis() - redisStart;
                boolean healthy = "PONG".equalsIgnoreCase(ping);
                services.put("Redis Cache", AdminSystemHealthResponse.ServiceStatus.builder()
                        .status(healthy ? "HEALTHY" : "DEGRADED")
                        .latencyMs(latency)
                        .message("Redis Cluster / Standalone phản hồi: " + ping)
                        .build());
                if (!healthy) isAllHealthy = false;
            } catch (Exception e) {
                log.warn("Redis health check failed: {}", e.getMessage());
                services.put("Redis Cache", AdminSystemHealthResponse.ServiceStatus.builder()
                        .status("DEGRADED")
                        .message("Redis không phản hồi, hệ thống đang dùng fallback bộ nhớ: " + e.getMessage())
                        .build());
            }
        } else {
            services.put("Redis Cache", AdminSystemHealthResponse.ServiceStatus.builder()
                    .status("DEGRADED")
                    .message("Redis chưa cấu hình kết nối, hệ thống hoạt động không có L2 Cache")
                    .build());
        }

        // 3. AI Reconstruction Pipeline Check
        long failedJobs = reconstructionJobRepository.countByStatus(ReconstructionJobStatus.failed);
        long queuedJobs = reconstructionJobRepository.countByStatus(ReconstructionJobStatus.queued);
        String workerStatus = failedJobs > 10 ? "DEGRADED" : "HEALTHY";
        services.put("3D Reconstruction Worker", AdminSystemHealthResponse.ServiceStatus.builder()
                .status(workerStatus)
                .message(String.format("Hàng đợi: %d đang chờ, %d lỗi ghi nhận", queuedJobs, failedJobs))
                .build());
        if ("DEGRADED".equals(workerStatus)) isAllHealthy = false;

        // 4. Hardware Printer Gateway Telemetry Check
        long onlinePrinters = printerRepository.countByOnlineTrue();
        long totalPrinters = printerRepository.count();
        services.put("Printer Telemetry Gateway", AdminSystemHealthResponse.ServiceStatus.builder()
                .status("HEALTHY")
                .message(String.format("%d / %d máy in đang gửi tín hiệu telemetry trực tuyến", onlinePrinters, totalPrinters))
                .build());

        return AdminSystemHealthResponse.builder()
                .status(isAllHealthy ? "HEALTHY" : "DEGRADED")
                .timestamp(Instant.now())
                .services(services)
                .build();
    }
}
