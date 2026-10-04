package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.GroupedSystemConfigResponse;
import com.memeto.team.memeto3d.dto.SystemConfigItemResponse;
import com.memeto.team.memeto3d.dto.UpdateSystemConfigRequest;

import java.util.UUID;

public interface SystemConfigService {

    GroupedSystemConfigResponse getAllConfigsGroupedByPrefix();

    SystemConfigItemResponse updateConfig(String key, UpdateSystemConfigRequest request, UUID adminUserId);
}
