package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.GoodsReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, UUID> {
    List<GoodsReceiptItem> findByReceiptId(UUID receiptId);
    boolean existsByMaterialId(UUID materialId);
}
