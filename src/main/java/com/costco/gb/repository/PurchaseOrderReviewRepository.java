package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseOrderReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface PurchaseOrderReviewRepository extends JpaRepository<PurchaseOrderReview, Long> {
    boolean existsByPurchaseOrderIdAndReviewerIdAndRevieweeId(Long orderId, Long reviewerId, Long revieweeId);
    boolean existsByReviewerIdAndRevieweeIdAndIsScoreCountedTrueAndCreatedAtAfter(
            Long reviewerId, Long revieweeId, LocalDateTime after);
    Page<PurchaseOrderReview> findByRevieweeIdOrderByCreatedAtDesc(Long revieweeId, Pageable pageable);
}
