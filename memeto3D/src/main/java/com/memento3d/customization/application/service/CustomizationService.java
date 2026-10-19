package com.memento3d.customization.application.service;

import com.memento3d.customization.domain.model.CustomizationOption;

import java.util.List;
import java.util.UUID;

public interface CustomizationService {
    List<CustomizationOption> getActiveOptions(UUID productId, UUID categoryId);
    List<CustomizationOption> getAllOptions();
    CustomizationOption createOption(CustomizationOption option);
    CustomizationOption updateOption(UUID id, CustomizationOption option);
}
