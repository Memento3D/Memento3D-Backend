package com.memento3d.products.infrastructure.persistence.repository;

import com.memento3d.products.domain.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    Optional<ProductCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<ProductCategory> findByActiveTrueOrderByDisplayOrderAsc();

    List<ProductCategory> findAllByOrderByDisplayOrderAsc();
}
