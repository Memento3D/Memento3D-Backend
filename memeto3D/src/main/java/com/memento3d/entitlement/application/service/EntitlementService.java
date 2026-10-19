package com.memento3d.entitlement.application.service;

import com.memento3d.entitlement.domain.model.Entitlement;
import com.memento3d.entitlement.domain.model.EntitlementType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EntitlementService {
    Entitlement grantEntitlement(UUID ownerId, UUID orderItemId, UUID resourceId, String resourceType, EntitlementType type, Instant validUntil);
    boolean hasEntitlement(UUID ownerId, UUID resourceId, EntitlementType type);
    void requireEntitlement(UUID ownerId, UUID resourceId, EntitlementType type);
    void revokeEntitlementsForOrderItem(UUID orderItemId);
    List<Entitlement> getMyEntitlements(UUID ownerId);
}
