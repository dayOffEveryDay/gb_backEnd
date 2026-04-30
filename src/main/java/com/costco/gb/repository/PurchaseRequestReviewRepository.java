package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseRequestReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PurchaseRequestReviewRepository extends JpaRepository<PurchaseRequestReview, Long> {

    boolean existsByPurchaseRequestIdAndReviewerIdAndRevieweeId(Long purchaseRequestId, Long reviewerId, Long revieweeId);

    boolean existsByReviewerIdAndRevieweeIdAndIsScoreCountedTrueAndCreatedAtAfter(
            Long reviewerId, Long revieweeId, LocalDateTime createdAt);

    Optional<PurchaseRequestReview> findByPurchaseRequestIdAndReviewerIdAndRevieweeId(
            Long purchaseRequestId, Long reviewerId, Long revieweeId);

    Page<PurchaseRequestReview> findByRevieweeIdOrderByCreatedAtDesc(Long revieweeId, Pageable pageable);
}
