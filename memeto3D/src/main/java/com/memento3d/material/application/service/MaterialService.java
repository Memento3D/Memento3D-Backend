package com.memento3d.material.application.service;

import com.memento3d.material.api.request.CreateMaterialRequest;
import com.memento3d.material.api.request.UpdateMaterialRequest;
import com.memento3d.material.api.response.MaterialResponse;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.domain.model.MaterialType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MaterialService {

    MaterialResponse createMaterial(CreateMaterialRequest request, UUID actorId);

    default MaterialResponse createMaterial(CreateMaterialRequest request) {
        return createMaterial(request, null);
    }

    MaterialResponse getMaterial(UUID id);

    Page<MaterialResponse> listMaterials(MaterialStatus status, MaterialType materialType, String category, String search, Pageable pageable);

    default Page<MaterialResponse> listMaterials(MaterialStatus status, MaterialType materialType, String search, Pageable pageable) {
        return listMaterials(status, materialType, null, search, pageable);
    }

    MaterialResponse updateMaterial(UUID id, UpdateMaterialRequest request, UUID actorId);

    default MaterialResponse updateMaterial(UUID id, UpdateMaterialRequest request) {
        return updateMaterial(id, request, null);
    }

    MaterialResponse updateMaterialStatus(UUID id, MaterialStatus status, UUID actorId);

    MaterialResponse deactivateMaterial(UUID id);
}
