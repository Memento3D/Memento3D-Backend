package com.memento3d.models.application.service;

import com.memento3d.models.api.response.ModelOutputResponse;
import com.memento3d.identity.domain.model.Role;

import java.util.List;
import java.util.UUID;

public interface ModelOutputService {

    List<ModelOutputResponse> getScanOutputs(
            UUID scanId,
            UUID currentUserId,
            Role currentUserRole
    );

    ModelOutputResponse getModelOutputById(
            UUID modelId,
            UUID currentUserId,
            Role currentUserRole
    );

    void deleteModelOutput(
            UUID modelId,
            UUID currentUserId,
            Role currentUserRole
    );
}