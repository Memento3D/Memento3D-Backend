package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.ModelOutputResponse;
import com.memeto.team.memeto3d.entity.Role;

import java.util.List;
import java.util.UUID;

public interface ModelOutputService {

    List<ModelOutputResponse> getScanOutputs(
            UUID scanId,
            UUID currentUserId,
            Role currentUserRole
    );
}