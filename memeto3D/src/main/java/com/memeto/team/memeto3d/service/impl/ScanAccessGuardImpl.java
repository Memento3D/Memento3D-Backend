package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.exception.InvalidTokenException;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.service.ScanAccessGuard;
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