package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.dto.GroupedSystemConfigResponse;
import com.memeto.team.memeto3d.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/configs")
@RequiredArgsConstructor
@Slf4j
public class AdminConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupedSystemConfigResponse> getAllConfigs() {
        GroupedSystemConfigResponse response = systemConfigService.getAllConfigsGroupedByPrefix();
        return ResponseEntity.ok(response);
    }
}
