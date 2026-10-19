package com.memento3d.customization.application.service.impl;

import com.memento3d.customization.application.service.CustomizationService;
import com.memento3d.customization.domain.model.CustomizationOption;
import com.memento3d.customization.infrastructure.persistence.repository.CustomizationOptionRepository;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomizationServiceImpl implements CustomizationService {

    private final CustomizationOptionRepository customizationOptionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CustomizationOption> getActiveOptions(UUID productId, UUID categoryId) {
        if (productId != null) {
            return customizationOptionRepository.findByProductIdAndIsActiveTrue(productId);
        }
        if (categoryId != null) {
            return customizationOptionRepository.findByCategoryIdAndIsActiveTrue(categoryId);
        }
        return customizationOptionRepository.findByIsActiveTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomizationOption> getAllOptions() {
        return customizationOptionRepository.findAll();
    }

    @Override
    @Transactional
    public CustomizationOption createOption(CustomizationOption option) {
        log.info("Creating customization option: {}", option.getName());
        return customizationOptionRepository.save(option);
    }

    @Override
    @Transactional
    public CustomizationOption updateOption(UUID id, CustomizationOption option) {
        CustomizationOption existing = customizationOptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customization option not found: " + id));

        if (option.getName() != null) existing.setName(option.getName());
        if (option.getAdditionalPrice() != null) existing.setAdditionalPrice(option.getAdditionalPrice());
        if (option.getOptionType() != null) existing.setOptionType(option.getOptionType());
        existing.setRequiresManualReview(option.isRequiresManualReview());
        existing.setActive(option.isActive());
        if (option.getMetadata() != null) existing.setMetadata(option.getMetadata());

        return customizationOptionRepository.save(existing);
    }
}
