package com.memento3d.material.application.service.impl;

import com.memento3d.material.api.request.CreateMaterialRequest;
import com.memento3d.material.api.request.UpdateMaterialRequest;
import com.memento3d.material.api.response.MaterialResponse;
import com.memento3d.material.application.service.MaterialService;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.domain.model.MaterialType;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.shared.application.service.AuditLogService;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialServiceImpl implements MaterialService {

    private final MaterialRepository materialRepository;
    private final AuditLogService auditLogService;

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
                .category(request.getCategory() != null ? request.getCategory().trim() : "Standard")
                .unit(request.getUnit() != null ? request.getUnit().trim() : "g")
                .brand(request.getBrand() != null ? request.getBrand().trim() : null)
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
                .minimumStock(request.getMinimumStock() != null ? request.getMinimumStock() : new BigDecimal("500.00"))
                .status(MaterialStatus.ACTIVE)
                .description(request.getDescription())
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();

        Material saved = materialRepository.save(material);
        log.info("Created material ID={}, SKU={}", saved.getId(), saved.getSku());

        auditLogService.log(actorId, "CREATE_MATERIAL", "Material", saved.getId().toString(), null, "Created SKU: " + cleanSku);

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
    public Page<MaterialResponse> listMaterials(MaterialStatus status, MaterialType materialType, String category, String search, Pageable pageable) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String cleanCategory = (category != null && !category.isBlank()) ? category.trim() : null;
        if (status == null && materialType == null && cleanCategory == null && cleanSearch == null) {
            return materialRepository.findAll(pageable).map(MaterialResponse::fromEntity);
        }
        return materialRepository.findAllWithFilters(status, materialType, cleanCategory, cleanSearch, pageable)
                .map(MaterialResponse::fromEntity);
    }

    @Override
    @Transactional
    public MaterialResponse updateMaterial(UUID id, UpdateMaterialRequest request, UUID actorId) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found with id: " + id));

        if (request.getName() != null && !request.getName().isBlank()) {
            material.setName(request.getName().trim());
        }
        if (request.getCategory() != null) {
            material.setCategory(request.getCategory().trim());
        }
        if (request.getUnit() != null) {
            material.setUnit(request.getUnit().trim());
        }
        if (request.getBrand() != null) {
            material.setBrand(request.getBrand().trim());
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
        if (request.getMinimumStock() != null) {
            material.setMinimumStock(request.getMinimumStock());
        }
        if (request.getDescription() != null) {
            material.setDescription(request.getDescription());
        }
        material.setUpdatedBy(actorId);

        Material saved = materialRepository.save(material);
        log.info("Updated material ID={}", saved.getId());

        auditLogService.log(actorId, "UPDATE_MATERIAL", "Material", saved.getId().toString(), null, "Updated SKU: " + saved.getSku());

        return MaterialResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public MaterialResponse updateMaterialStatus(UUID id, MaterialStatus status, UUID actorId) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found with id: " + id));
        material.setStatus(status);
        material.setUpdatedBy(actorId);
        Material saved = materialRepository.save(material);
        log.info("Updated material ID={} status to {}", saved.getId(), status);

        auditLogService.log(actorId, "UPDATE_MATERIAL_STATUS", "Material", saved.getId().toString(), null, "Status: " + status);

        return MaterialResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public MaterialResponse deactivateMaterial(UUID id) {
        return updateMaterialStatus(id, MaterialStatus.INACTIVE, null);
    }
}
