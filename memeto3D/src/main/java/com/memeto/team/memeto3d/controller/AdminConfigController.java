package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.dto.GroupedSystemConfigResponse;
import com.memeto.team.memeto3d.dto.SystemConfigItemResponse;
import com.memeto.team.memeto3d.dto.UpdateSystemConfigRequest;
import com.memeto.team.memeto3d.exception.InvalidTokenException;
import com.memeto.team.memeto3d.security.CustomUserDetails;
import com.memeto.team.memeto3d.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemConfigItemResponse> updateConfig(
            @PathVariable("key") String key,
            @Valid @RequestBody UpdateSystemConfigRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID adminId = getAuthenticatedUserId(userDetails);
        SystemConfigItemResponse response = systemConfigService.updateConfig(key, request, adminId);
        return ResponseEntity.ok(response);
    }

    private UUID getAuthenticatedUserId(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getId() != null) {
            return userDetails.getId();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidTokenException("No authenticated user in security context");
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getId();
        }
        throw new InvalidTokenException("Invalid user details in security context");
    }
}
