package com.memento3d.pricing.infrastructure.persistence.repository;

import com.memento3d.pricing.domain.model.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {
    List<PricingRule> findByActiveTrueOrderByPriorityAsc();
    Optional<PricingRule> findByCode(String code);
    List<PricingRule> findByProductIdAndActiveTrue(UUID productId);
    List<PricingRule> findByCategoryIdAndActiveTrue(UUID categoryId);
}
