package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.GroupedSystemConfigResponse;

public interface SystemConfigService {

    GroupedSystemConfigResponse getAllConfigsGroupedByPrefix();
}
