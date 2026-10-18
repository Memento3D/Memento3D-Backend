package com.memento3d.material.application.service.impl;

import com.memento3d.material.api.request.CreateMaterialRequest;
import com.memento3d.material.api.request.UpdateMaterialRequest;
import com.memento3d.material.api.response.MaterialResponse;
import com.memento3d.material.application.service.MaterialService;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.domain.model.MaterialType;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialServiceImpl implements MaterialService {

    private final MaterialRepository materialRepository;

    @Override
    @Transactional
    public MaterialResponse createMaterial(CreateMaterialRequest request, UUID actorId) {
        String cleanSku = request.getSku().trim();
        if (materialRepository.existsBySkuIgnoreCase(cleanSku)) {
            throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_SKU", "Material with SKU '" + cleanSku + "' already exists");
        }

        Material material = Material.builder()
                .sku(cleanSku)
                .name(request.getName().trim())
                .materialType(request.getMaterialType())
                .color(request.getColor().trim())
                .colorHex(request.getColorHex())
                .density(request.getDensity())
                .diameter(request.getDiameter())
                .printTempMin(request.getPrintTempMin())
                .printTempMax(request.getPrintTempMax())
                .bedTempMin(request.getBedTempMin())
                .bedTempMax(request.getBedTempMax())
                .unitCost(request.getUnitCost())
                .status(MaterialStatus.ACTIVE)
                .description(request.getDescription())
                .createdBy(actorId)
                .build();

        Material saved = materialRepository.save(material);
        log.info("Created material ID={}, SKU={}", saved.getId(), saved.getSku());
        return MaterialResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialResponse getMaterial(UUID id) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found with id: " + id));
        return MaterialResponse.fromEntity(material);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MaterialResponse> listMaterials(MaterialStatus status, MaterialType materialType, String search, Pageable pageable) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        if (status == null && materialType == null && cleanSearch == null) {
            return materialRepository.findAll(pageable).map(MaterialResponse::fromEntity);
        }
        return materialRepository.findAllWithFilters(status, materialType, cleanSearch, pageable)
                .map(MaterialResponse::fromEntity);
    }

    @Override
    @Transactional
    public MaterialResponse updateMaterial(UUID id, UpdateMaterialRequest request) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found with id: " + id));

        if (request.getSku() != null && !request.getSku().isBlank()) {
            String newSku = request.getSku().trim();
            if (materialRepository.existsBySkuIgnoreCaseAndIdNot(newSku, id)) {
                throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_SKU", "Material with SKU '" + newSku + "' already exists");
            }
            material.setSku(newSku);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            material.setName(request.getName().trim());
        }
        if (request.getMaterialType() != null) {
            material.setMaterialType(request.getMaterialType());
        }
        if (request.getColor() != null && !request.getColor().isBlank()) {
            material.setColor(request.getColor().trim());
        }
        if (request.getColorHex() != null) {
            material.setColorHex(request.getColorHex());
        }
        if (request.getDensity() != null) {
            material.setDensity(request.getDensity());
        }
        if (request.getDiameter() != null) {
            material.setDiameter(request.getDiameter());
        }
        if (request.getPrintTempMin() != null) {
            material.setPrintTempMin(request.getPrintTempMin());
        }
        if (request.getPrintTempMax() != null) {
            material.setPrintTempMax(request.getPrintTempMax());
        }
        if (request.getBedTempMin() != null) {
            material.setBedTempMin(request.getBedTempMin());
        }
        if (request.getBedTempMax() != null) {
            material.setBedTempMax(request.getBedTempMax());
        }
        if (request.getUnitCost() != null) {
            material.setUnitCost(request.getUnitCost());
        }
        if (request.getStatus() != null) {
            material.setStatus(request.getStatus());
        }
        if (request.getDescription() != null) {
            material.setDescription(request.getDescription());
        }

        Material saved = materialRepository.save(material);
        log.info("Updated material ID={}", saved.getId());
        return MaterialResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public MaterialResponse deactivateMaterial(UUID id) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found with id: " + id));
        material.setStatus(MaterialStatus.INACTIVE);
        Material saved = materialRepository.save(material);
        log.info("Deactivated material ID={}", saved.getId());
        return MaterialResponse.fromEntity(saved);
    }
}
