package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PurchaseQuoteRepository extends JpaRepository<PurchaseQuote, Long> {

    boolean existsByPurchaseRequestIdAndStatus(Long requestId, String status);

    boolean existsByPurchaseRequestIdAndQuoterId(Long requestId, Long quoterId);

    List<PurchaseQuote> findByPurchaseRequestIdOrderByCreatedAtDesc(Long requestId);
    List<PurchaseQuote> findByPurchaseRequestIdAndStatus(Long requestId, String status);

    List<PurchaseQuote> findByPurchaseRequestIdAndStatusAndIdNot(
            Long requestId,
            String status,
            Long excludedId
    );

    Page<PurchaseQuote> findByQuoterIdOrderByCreatedAtDesc(Long quoterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pq FROM PurchaseQuote pq WHERE pq.id = :id")
    Optional<PurchaseQuote> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("UPDATE PurchaseQuote pq SET pq.status = 'EXPIRED' " +
            "WHERE pq.purchaseRequest.id = :requestId AND pq.status = 'ACTIVE'")
    int expireActiveQuotes(@Param("requestId") Long requestId);

    @Modifying
    @Query("UPDATE PurchaseQuote pq SET pq.status = 'NOT_SELECTED' " +
            "WHERE pq.purchaseRequest.id = :requestId AND pq.status = 'ACTIVE' AND pq.id <> :selectedId")
    int markOtherActiveQuotesNotSelected(
            @Param("requestId") Long requestId,
            @Param("selectedId") Long selectedId
    );
}
