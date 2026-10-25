package com.memento3d.products.infrastructure.persistence.repository;

import com.memento3d.products.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findBySlug(String slug);

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findWithCategoryById(@Param("id") UUID id);

    boolean existsBySlug(String slug);

    long countByCategoryId(UUID categoryId);

    @Query("SELECT p.category.id, COUNT(p) FROM Product p GROUP BY p.category.id")
    List<Object[]> countAllProductsGroupedByCategory();

    @Query("SELECT p.category.id, COUNT(p) FROM Product p WHERE p.active = true GROUP BY p.category.id")
    List<Object[]> countActiveProductsGroupedByCategory();

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByFeaturedTrueAndActiveTrue();

    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    @Query(
        value = """
            SELECT p FROM Product p WHERE
            (:active IS NULL OR p.active = :active) AND
            (:featured IS NULL OR p.featured = :featured) AND
            (:categoryId IS NULL OR p.category.id = :categoryId) AND
            (:categorySlug IS NULL OR p.category.slug = :categorySlug) AND
            (:minPrice IS NULL OR p.price >= :minPrice) AND
            (:maxPrice IS NULL OR p.price <= :maxPrice) AND
            (CAST(:keyword AS string) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
              OR LOWER(p.description) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
            """,
        countQuery = """
            SELECT COUNT(p) FROM Product p WHERE
            (:active IS NULL OR p.active = :active) AND
            (:featured IS NULL OR p.featured = :featured) AND
            (:categoryId IS NULL OR p.category.id = :categoryId) AND
            (:categorySlug IS NULL OR p.category.slug = :categorySlug) AND
            (:minPrice IS NULL OR p.price >= :minPrice) AND
            (:maxPrice IS NULL OR p.price <= :maxPrice) AND
            (CAST(:keyword AS string) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
              OR LOWER(p.description) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
            """
    )
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("categorySlug") String categorySlug,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("active") Boolean active,
            @Param("featured") Boolean featured,
            Pageable pageable
    );
}
