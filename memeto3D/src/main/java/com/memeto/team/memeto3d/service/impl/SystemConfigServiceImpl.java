package com.memeto.team.memeto3d.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memeto.team.memeto3d.dto.GroupedSystemConfigResponse;
import com.memeto.team.memeto3d.dto.SystemConfigItemResponse;
import com.memeto.team.memeto3d.dto.UpdateSystemConfigRequest;
import com.memeto.team.memeto3d.entity.SystemConfig;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.SystemConfigRepository;
import com.memeto.team.memeto3d.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final ObjectMapper objectMapper;

    @Override
    public GroupedSystemConfigResponse getAllConfigsGroupedByPrefix() {
        List<SystemConfig> configs = systemConfigRepository.findAllByOrderByConfigKeyAsc();
        Map<String, List<SystemConfigItemResponse>> groups = new HashMap<>();

        for (SystemConfig config : configs) {
            String key = config.getConfigKey();
            String prefix = "general";
            if (key != null && key.contains(".")) {
                prefix = key.substring(0, key.indexOf("."));
            }

            SystemConfigItemResponse item = new SystemConfigItemResponse();
            item.setId(config.getId());
            item.setConfigKey(config.getConfigKey());
            item.setConfigValue(config.getConfigValue());
            item.setValueType(config.getValueType());
            item.setDescription(config.getDescription());
            item.setUpdatedAt(config.getUpdatedAt());

            if (!groups.containsKey(prefix)) {
                groups.put(prefix, new ArrayList<>());
            }
            groups.get(prefix).add(item);
        }

        GroupedSystemConfigResponse response = new GroupedSystemConfigResponse();
        response.setTotalConfigs(configs.size());
        response.setGroups(groups);
        return response;
    }

    @Override
    public SystemConfigItemResponse updateConfig(String key, UpdateSystemConfigRequest request, UUID adminUserId) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("System config with key '" + key + "' not found"));

        validateValueType(request.getConfigValue(), config.getValueType());

        config.setConfigValue(request.getConfigValue());
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
        config.setUpdatedBy(adminUserId);

        SystemConfig updated = systemConfigRepository.save(config);

        SystemConfigItemResponse response = new SystemConfigItemResponse();
        response.setId(updated.getId());
        response.setConfigKey(updated.getConfigKey());
        response.setConfigValue(updated.getConfigValue());
        response.setValueType(updated.getValueType());
        response.setDescription(updated.getDescription());
        response.setUpdatedAt(updated.getUpdatedAt());
        return response;
    }

    private void validateValueType(String value, String valueType) {
        if (valueType == null) {
            return;
        }
        switch (valueType.toLowerCase()) {
            case "integer" -> validateInteger(value);
            case "decimal" -> validateDecimal(value);
            case "boolean" -> validateBoolean(value);
            case "json" -> validateJson(value);
        }
    }

    private void validateInteger(String value) {
        try {
            Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value for integer type: " + value);
        }
    }

    private void validateDecimal(String value) {
        try {
            Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value for decimal type: " + value);
        }
    }

    private void validateBoolean(String value) {
        String val = value.trim().toLowerCase();
        if (!val.equals("true") && !val.equals("false")) {
            throw new IllegalArgumentException("Invalid value for boolean type: " + value + ". Must be 'true' or 'false'");
        }
    }

    private void validateJson(String value) {
        try {
            objectMapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON format: " + value);
        }
    }
}
