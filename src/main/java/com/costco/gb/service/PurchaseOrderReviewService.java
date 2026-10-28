package com.costco.gb.service;

import com.costco.gb.logging.BusinessLog;
import com.costco.gb.dto.request.CreatePurchaseOrderReviewRequest;
import com.costco.gb.dto.response.PurchaseOrderReviewResponse;
import com.costco.gb.entity.*;
import com.costco.gb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Slf4j
@Service @RequiredArgsConstructor
public class PurchaseOrderReviewService {
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderReviewRepository reviewRepository;
    private final UserRepository userRepository;

    @Transactional
    public PurchaseOrderReviewResponse create(Long userId, Long orderId, CreatePurchaseOrderReviewRequest request) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("找不到託購訂單"));
        if (!"COMPLETED".equals(order.getStatus())) throw new RuntimeException("只有已完成訂單可以評價");
        User reviewer;
        User reviewee;
        if (order.getRequester().getId().equals(userId)) {
            reviewer = order.getRequester(); reviewee = order.getRunner();
        } else if (order.getRunner().getId().equals(userId)) {
            reviewer = order.getRunner(); reviewee = order.getRequester();
        } else throw new RuntimeException("只有交易雙方可以評價");
        if (reviewRepository.existsByPurchaseOrderIdAndReviewerIdAndRevieweeId(orderId, userId, reviewee.getId()))
            throw new RuntimeException("您已評價過此訂單");
        int rating = request.getRating() == null ? 0 : request.getRating();
        if (rating < 1 || rating > 5) throw new RuntimeException("星等必須介於 1 到 5");

        int change = rating == 5 ? 1 : rating == 1 ? -3 : 0;
        boolean counted = change != 0;
        if (change > 0 && reviewRepository.existsByReviewerIdAndRevieweeIdAndIsScoreCountedTrueAndCreatedAtAfter(
                reviewer.getId(), reviewee.getId(), LocalDateTime.now().minusDays(7))) {
            change = 0; counted = false;
            BusinessLog.info(log, "event=PURCHASE_REVIEW_SCORE_SUPPRESSED actorId={} revieweeId={} orderId={} reasonCode=RECENT_POSITIVE_REVIEW", userId, reviewee.getId(), orderId);
        }
        PurchaseOrderReview review = reviewRepository.save(PurchaseOrderReview.builder()
                .purchaseOrder(order).reviewer(reviewer).reviewee(reviewee).rating(rating)
                .comment(request.getComment()).isScoreCounted(counted).build());
        if (change != 0) {
            int score = reviewee.getCreditScore() == null ? 100 : reviewee.getCreditScore();
            reviewee.setCreditScore(Math.max(0, Math.min(100, score + change)));
            userRepository.save(reviewee);
            BusinessLog.info(log, "event=PURCHASE_REVIEW_CREDIT_CHANGED actorId={} userId={} orderId={} reviewId={} beforeScore={} afterScore={} actualChange={}", userId, reviewee.getId(), orderId, review.getId(), score, reviewee.getCreditScore(), reviewee.getCreditScore() - score);
        }
        BusinessLog.info(log, "event=PURCHASE_REVIEW_CREATED actorId={} revieweeId={} orderId={} reviewId={} rating={} scoreCounted={}", userId, reviewee.getId(), orderId, review.getId(), rating, counted);
        return response(review);
    }

    @Transactional(readOnly = true)
    public boolean status(Long userId, Long orderId) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("找不到託購訂單"));
        Long other = order.getRequester().getId().equals(userId) ? order.getRunner().getId()
                : order.getRunner().getId().equals(userId) ? order.getRequester().getId() : null;
        if (other == null) throw new RuntimeException("只有交易雙方可以查詢評價狀態");
        return reviewRepository.existsByPurchaseOrderIdAndReviewerIdAndRevieweeId(orderId, userId, other);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderReviewResponse> received(Long userId, int page, int size) {
        return reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size)).map(this::response);
    }

    private PurchaseOrderReviewResponse response(PurchaseOrderReview r) {
        return PurchaseOrderReviewResponse.builder().id(r.getId()).orderId(r.getPurchaseOrder().getId())
                .itemName(r.getPurchaseOrder().getPurchaseRequest().getItemName())
                .reviewerId(r.getReviewer().getId()).reviewerName(r.getReviewer().getDisplayName())
                .revieweeId(r.getReviewee().getId()).rating(r.getRating()).comment(r.getComment())
                .isScoreCounted(r.getIsScoreCounted()).createdAt(r.getCreatedAt()).build();
    }
}
