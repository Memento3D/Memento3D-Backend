package com.memento3d.entitlement.application.service.impl;

import com.memento3d.entitlement.application.service.EntitlementService;
import com.memento3d.entitlement.domain.exception.EntitlementRequiredException;
import com.memento3d.entitlement.domain.model.Entitlement;
import com.memento3d.entitlement.domain.model.EntitlementStatus;
import com.memento3d.entitlement.domain.model.EntitlementType;
import com.memento3d.entitlement.infrastructure.persistence.repository.EntitlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntitlementServiceImpl implements EntitlementService {

    private final EntitlementRepository entitlementRepository;

    @Override
    @Transactional
    public Entitlement grantEntitlement(
            UUID ownerId,
            UUID orderItemId,
            UUID resourceId,
            String resourceType,
            EntitlementType type,
            Instant validUntil
    ) {
        log.info("Granting entitlement: owner={}, resource={}, type={}", ownerId, resourceId, type);
        Optional<Entitlement> existing = entitlementRepository.findByOwnerIdAndResourceIdAndEntitlementTypeAndStatus(
                ownerId, resourceId, type, EntitlementStatus.ACTIVE
        );
        if (existing.isPresent()) {
            Entitlement ent = existing.get();
            if (validUntil != null) {
                ent.setValidUntil(validUntil);
            }
            return entitlementRepository.save(ent);
        }

        Entitlement entitlement = Entitlement.builder()
                .ownerId(ownerId)
                .orderItemId(orderItemId)
                .resourceId(resourceId)
                .resourceType(resourceType)
                .entitlementType(type)
                .status(EntitlementStatus.ACTIVE)
                .validUntil(validUntil)
                .build();

        return entitlementRepository.save(entitlement);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasEntitlement(UUID ownerId, UUID resourceId, EntitlementType type) {
        return entitlementRepository.findByOwnerIdAndResourceIdAndEntitlementTypeAndStatus(
                ownerId, resourceId, type, EntitlementStatus.ACTIVE
        ).map(Entitlement::isValid).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireEntitlement(UUID ownerId, UUID resourceId, EntitlementType type) {
        if (!hasEntitlement(ownerId, resourceId, type)) {
            log.warn("Access denied: user {} does not have entitlement {} on resource {}", ownerId, type, resourceId);
            throw new EntitlementRequiredException("You do not have the required entitlement (" + type + ") for this resource.");
        }
    }

    @Override
    @Transactional
    public void revokeEntitlementsForOrderItem(UUID orderItemId) {
        List<Entitlement> list = entitlementRepository.findByOrderItemId(orderItemId);
        for (Entitlement e : list) {
            e.setStatus(EntitlementStatus.REVOKED);
        }
        entitlementRepository.saveAll(list);
        log.info("Revoked {} entitlements for orderItem {}", list.size(), orderItemId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Entitlement> getMyEntitlements(UUID ownerId) {
        return entitlementRepository.findByOwnerIdAndStatus(ownerId, EntitlementStatus.ACTIVE);
    }
}
