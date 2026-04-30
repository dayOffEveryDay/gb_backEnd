package com.costco.gb.service;

import com.costco.gb.dto.request.CreatePurchaseRequestReviewRequest;
import com.costco.gb.dto.response.PurchaseRequestReviewResponse;
import com.costco.gb.dto.response.ReviewStatusResponse;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.PurchaseRequestReview;
import com.costco.gb.entity.User;
import com.costco.gb.repository.PurchaseRequestRepository;
import com.costco.gb.repository.PurchaseRequestReviewRepository;
import com.costco.gb.repository.ReviewRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRequestReviewService {

    private final PurchaseRequestReviewRepository purchaseRequestReviewRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final CreditScoreService creditScoreService;

    @Transactional
    public PurchaseRequestReviewResponse createReview(Long requestId, Long reviewerId, CreatePurchaseRequestReviewRequest request) {
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new RuntimeException("評價星等必須介於 1 到 5");
        }

        PurchaseRequest purchaseRequest = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("找不到託購單"));
        if (!"COMPLETED".equals(purchaseRequest.getStatus())) {
            throw new RuntimeException("託購單完成後才能評價");
        }
        if (purchaseRequest.getAssignedRunner() == null) {
            throw new RuntimeException("託購單沒有承接跑腿者，不能評價");
        }

        User reviewee = resolveReviewee(purchaseRequest, reviewerId);
        if (reviewerId.equals(reviewee.getId())) {
            throw new RuntimeException("不能評價自己");
        }
        boolean alreadyReviewed = purchaseRequestReviewRepository.existsByPurchaseRequestIdAndReviewerIdAndRevieweeId(
                requestId, reviewerId, reviewee.getId());
        if (alreadyReviewed) {
            throw new RuntimeException("你已經評價過這張託購單的對方");
        }

        User reviewer = userRepository.getReferenceById(reviewerId);
        ScoreResult scoreResult = calculateScoreResult(reviewerId, reviewee.getId(), request.getRating());

        PurchaseRequestReview review = PurchaseRequestReview.builder()
                .purchaseRequest(purchaseRequest)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .rating(request.getRating())
                .comment(request.getComment())
                .isScoreCounted(scoreResult.isCounted())
                .build();
        PurchaseRequestReview savedReview = purchaseRequestReviewRepository.save(review);

        creditScoreService.recordPurchaseRequestScoreChange(
                reviewee.getId(),
                scoreResult.scoreChange(),
                scoreResult.reason(),
                purchaseRequest.getId());

        log.info("Purchase request {} reviewed by {} for {}, rating {}",
                requestId, reviewerId, reviewee.getId(), request.getRating());

        return toResponse(savedReview);
    }

    @Transactional(readOnly = true)
    public ReviewStatusResponse checkReviewStatus(Long requestId, Long reviewerId) {
        PurchaseRequest purchaseRequest = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("找不到託購單"));
        User reviewee = resolveReviewee(purchaseRequest, reviewerId);

        return purchaseRequestReviewRepository.findByPurchaseRequestIdAndReviewerIdAndRevieweeId(
                        requestId, reviewerId, reviewee.getId())
                .map(review -> ReviewStatusResponse.builder()
                        .isReviewed(true)
                        .rating(review.getRating())
                        .comment(review.getComment())
                        .build())
                .orElse(ReviewStatusResponse.builder()
                        .isReviewed(false)
                        .build());
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequestReviewResponse> getMyReceivedReviews(Long revieweeId, Pageable pageable) {
        return purchaseRequestReviewRepository.findByRevieweeIdOrderByCreatedAtDesc(revieweeId, pageable)
                .map(this::toResponse);
    }

    private User resolveReviewee(PurchaseRequest purchaseRequest, Long reviewerId) {
        if (purchaseRequest.getRequester().getId().equals(reviewerId)) {
            return purchaseRequest.getAssignedRunner();
        }
        if (purchaseRequest.getAssignedRunner() != null && purchaseRequest.getAssignedRunner().getId().equals(reviewerId)) {
            return purchaseRequest.getRequester();
        }
        throw new RuntimeException("只有委託人或承接跑腿者可以評價這張託購單");
    }

    private ScoreResult calculateScoreResult(Long reviewerId, Long revieweeId, Integer rating) {
        int scoreChange = 0;
        boolean isCounted = false;
        String reason = "託購收到 " + rating + " 星評價";

        if (rating == 5) {
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            boolean hasRecentCampaignPositiveReview = reviewRepository
                    .existsByReviewerIdAndRevieweeIdAndIsScoreCountedTrueAndCreatedAtAfter(
                            reviewerId, revieweeId, sevenDaysAgo);
            boolean hasRecentPurchasePositiveReview = purchaseRequestReviewRepository
                    .existsByReviewerIdAndRevieweeIdAndIsScoreCountedTrueAndCreatedAtAfter(
                            reviewerId, revieweeId, sevenDaysAgo);

            if (!hasRecentCampaignPositiveReview && !hasRecentPurchasePositiveReview) {
                scoreChange = 1;
                isCounted = true;
            } else {
                reason = "託購收到 5 星評價，7 天內同評價者正評不重複計分";
            }
        } else if (rating == 1) {
            scoreChange = -3;
            isCounted = true;
        }

        return new ScoreResult(scoreChange, isCounted, reason);
    }

    private PurchaseRequestReviewResponse toResponse(PurchaseRequestReview review) {
        return PurchaseRequestReviewResponse.builder()
                .id(review.getId())
                .purchaseRequestId(review.getPurchaseRequest().getId())
                .productName(review.getPurchaseRequest().getProductName())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getDisplayName())
                .revieweeId(review.getReviewee().getId())
                .revieweeName(review.getReviewee().getDisplayName())
                .rating(review.getRating())
                .comment(review.getComment())
                .isScoreCounted(review.getIsScoreCounted())
                .createdAt(review.getCreatedAt())
                .build();
    }

    private record ScoreResult(int scoreChange, boolean isCounted, String reason) {
    }
}
