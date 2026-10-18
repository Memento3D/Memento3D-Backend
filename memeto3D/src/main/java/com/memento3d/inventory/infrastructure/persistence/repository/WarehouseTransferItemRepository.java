package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.WarehouseTransferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WarehouseTransferItemRepository extends JpaRepository<WarehouseTransferItem, UUID> {
    List<WarehouseTransferItem> findByTransferId(UUID transferId);
}
