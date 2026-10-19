package com.memento3d.pricing.infrastructure.persistence.repository;

import com.memento3d.pricing.domain.model.PriceQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceQuoteRepository extends JpaRepository<PriceQuote, UUID> {

    @Query("SELECT q FROM PriceQuote q LEFT JOIN FETCH q.lineItems WHERE q.id = :id")
    Optional<PriceQuote> findByIdWithLineItems(@Param("id") UUID id);
}
