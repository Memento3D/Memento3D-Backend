package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.GroupedSystemConfigResponse;
import com.memeto.team.memeto3d.dto.SystemConfigItemResponse;
import com.memeto.team.memeto3d.entity.SystemConfig;
import com.memeto.team.memeto3d.repository.SystemConfigRepository;
import com.memeto.team.memeto3d.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    @Override
    @Transactional(readOnly = true)
    public GroupedSystemConfigResponse getAllConfigsGroupedByPrefix() {
        List<SystemConfig> configs = systemConfigRepository.findAllByOrderByConfigKeyAsc();

        Map<String, List<SystemConfigItemResponse>> grouped = configs.stream()
                .map(SystemConfigItemResponse::fromEntity)
                .collect(Collectors.groupingBy(
                        item -> extractPrefix(item.getConfigKey()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return GroupedSystemConfigResponse.builder()
                .totalConfigs(configs.size())
                .groups(grouped)
                .build();
    }

    private String extractPrefix(String key) {
        if (key == null || key.isBlank()) {
            return "general";
        }
        int dotIndex = key.indexOf('.');
        return (dotIndex > 0) ? key.substring(0, dotIndex) : "general";
    }
}
