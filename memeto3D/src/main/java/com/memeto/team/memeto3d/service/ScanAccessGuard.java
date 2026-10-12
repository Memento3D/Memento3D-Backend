package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.Scan;

import java.util.UUID;

public interface ScanAccessGuard {

    void requireAccess(
            Scan scan,
            UUID currentUserId,
            Role currentUserRole
    );
}