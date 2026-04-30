package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseRequestQuote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRequestQuoteRepository extends JpaRepository<PurchaseRequestQuote, Long> {

    long countByPurchaseRequestIdAndStatus(Long purchaseRequestId, String status);

    boolean existsByPurchaseRequestIdAndStatus(Long purchaseRequestId, String status);

    Optional<PurchaseRequestQuote> findByPurchaseRequestIdAndRunnerId(Long purchaseRequestId, Long runnerId);

    List<PurchaseRequestQuote> findByPurchaseRequestIdOrderByCreatedAtDesc(Long purchaseRequestId);

    Page<PurchaseRequestQuote> findByRunnerIdOrderByCreatedAtDesc(Long runnerId, Pageable pageable);

    @Modifying
    @Query("UPDATE PurchaseRequestQuote q SET q.status = 'REJECTED' " +
            "WHERE q.purchaseRequest.id = :requestId AND q.id <> :acceptedQuoteId AND q.status = 'PENDING'")
    int rejectOtherPendingQuotes(@Param("requestId") Long requestId, @Param("acceptedQuoteId") Long acceptedQuoteId);
}
