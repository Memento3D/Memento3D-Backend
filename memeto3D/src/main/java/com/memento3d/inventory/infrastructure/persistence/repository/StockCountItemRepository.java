package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.StockCountItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockCountItemRepository extends JpaRepository<StockCountItem, UUID> {
    List<StockCountItem> findByStockCountId(UUID stockCountId);
}
