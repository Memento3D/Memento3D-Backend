package com.memento3d.scans.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.scans.domain.model.Scan;

import java.util.UUID;

public interface ScanAccessGuard {

    void requireAccess(
            Scan scan,
            UUID currentUserId,
            Role currentUserRole
    );
}