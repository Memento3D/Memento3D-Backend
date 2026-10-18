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

    MaterialResponse getMaterial(UUID id);

    Page<MaterialResponse> listMaterials(MaterialStatus status, MaterialType materialType, String search, Pageable pageable);

    MaterialResponse updateMaterial(UUID id, UpdateMaterialRequest request);

    MaterialResponse deactivateMaterial(UUID id);
}
