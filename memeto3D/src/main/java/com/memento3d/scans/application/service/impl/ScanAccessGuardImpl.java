package com.memento3d.scans.application.service.impl;

import com.memento3d.identity.domain.model.User;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.scans.application.service.ScanAccessGuard;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ScanAccessGuardImpl implements ScanAccessGuard {

    @Override
    public void requireAccess(
            Scan scan,
            UUID currentUserId,
            Role currentUserRole
    ) {
        if (currentUserId == null) {
            throw new InvalidTokenException(
                    "Authenticated user is required"
            );
        }

        boolean privileged =
                currentUserRole == Role.ROLE_ADMIN
                        || currentUserRole == Role.ROLE_OPERATOR;

        boolean owner =
                scan.getUser() != null
                        && currentUserId.equals(scan.getUser().getId());

        if (!privileged && !owner) {
            throw new ResourceNotFoundException(
                    "Scan not found with ID: " + scan.getId()
            );
        }
    }
}