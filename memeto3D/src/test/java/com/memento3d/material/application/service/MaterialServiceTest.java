package com.memento3d.material.application.service;

import com.memento3d.material.api.request.CreateMaterialRequest;
import com.memento3d.material.api.request.UpdateMaterialRequest;
import com.memento3d.material.api.response.MaterialResponse;
import com.memento3d.material.application.service.impl.MaterialServiceImpl;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.domain.model.MaterialType;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private MaterialRepository materialRepository;

    @InjectMocks
    private MaterialServiceImpl materialService;

    private UUID materialId;
    private Material mockMaterial;

    @BeforeEach
    void setUp() {
        materialId = UUID.randomUUID();
        mockMaterial = Material.builder()
                .id(materialId)
                .sku("PLA-WHT-001")
                .name("eSun PLA+ Cold White")
                .materialType(MaterialType.PLA)
                .color("White")
                .unitCost(new BigDecimal("250.00"))
                .status(MaterialStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("createMaterial: succeeds when SKU is unique")
    void createMaterial_success() {
        CreateMaterialRequest request = CreateMaterialRequest.builder()
                .sku("PLA-WHT-001")
                .name("eSun PLA+ Cold White")
                .materialType(MaterialType.PLA)
                .color("White")
                .unitCost(new BigDecimal("250.00"))
                .build();

        when(materialRepository.existsBySkuIgnoreCase("PLA-WHT-001")).thenReturn(false);
        when(materialRepository.save(any(Material.class))).thenReturn(mockMaterial);

        MaterialResponse response = materialService.createMaterial(request, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.getSku()).isEqualTo("PLA-WHT-001");
        verify(materialRepository).save(any(Material.class));
    }

    @Test
    @DisplayName("createMaterial: throws 409 Conflict when SKU already exists")
    void createMaterial_duplicateSku() {
        CreateMaterialRequest request = CreateMaterialRequest.builder()
                .sku("PLA-WHT-001")
                .name("Duplicate SKU")
                .materialType(MaterialType.PLA)
                .color("White")
                .unitCost(new BigDecimal("250.00"))
                .build();

        when(materialRepository.existsBySkuIgnoreCase("PLA-WHT-001")).thenReturn(true);

        assertThatThrownBy(() -> materialService.createMaterial(request, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("DUPLICATE_SKU");
                });

        verify(materialRepository, never()).save(any(Material.class));
    }

    @Test
    @DisplayName("getMaterial: throws 404 when not found")
    void getMaterial_notFound() {
        when(materialRepository.findById(materialId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materialService.getMaterial(materialId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deactivateMaterial: sets status to INACTIVE")
    void deactivateMaterial_success() {
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(mockMaterial));
        when(materialRepository.save(any(Material.class))).thenAnswer(inv -> inv.getArgument(0));

        MaterialResponse response = materialService.deactivateMaterial(materialId);

        assertThat(response.getStatus()).isEqualTo(MaterialStatus.INACTIVE);
        assertThat(mockMaterial.getStatus()).isEqualTo(MaterialStatus.INACTIVE);
    }

    @Test
    @DisplayName("updateMaterial: updates fields cleanly")
    void updateMaterial_success() {
        UpdateMaterialRequest request = UpdateMaterialRequest.builder()
                .name("eSun PLA+ Warm White")
                .unitCost(new BigDecimal("260.00"))
                .build();

        when(materialRepository.findById(materialId)).thenReturn(Optional.of(mockMaterial));
        when(materialRepository.save(any(Material.class))).thenAnswer(inv -> inv.getArgument(0));

        MaterialResponse response = materialService.updateMaterial(materialId, request);

        assertThat(response.getName()).isEqualTo("eSun PLA+ Warm White");
        assertThat(response.getUnitCost()).isEqualTo(new BigDecimal("260.00"));
    }
}
