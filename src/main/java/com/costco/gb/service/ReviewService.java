package com.costco.gb.service;

import com.costco.gb.dto.request.CreateReviewRequest;
import com.costco.gb.dto.response.ReviewResponse;
import com.costco.gb.dto.response.ReviewStatusResponse;
import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.CreditScoreLog;
import com.costco.gb.entity.Review;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.CreditScoreLogRepository;
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
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;

    // 🌟 注入信用存摺
    private final CreditScoreLogRepository creditScoreLogRepository;

    @Transactional
    public void createReview(Long reviewerId, CreateReviewRequest request) {

        // 1. 防呆：不能自己評價自己
        if (reviewerId.equals(request.getRevieweeId())) {
            throw new RuntimeException("無法對自己進行評價！");
        }

        // 2. 防呆：星等必須在 1~5 之間
        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new RuntimeException("星等必須介於 1 到 5 之間！");
        }

        // 3. 防洗評價：檢查是否已經在這筆訂單評價過
        boolean alreadyReviewed = reviewRepository.existsByCampaignIdAndReviewerIdAndRevieweeId(
                request.getCampaignId(), reviewerId, request.getRevieweeId());
        if (alreadyReviewed) {
            throw new RuntimeException("您已經對此用戶在這筆交易中給過評價了，無法重複評價！");
        }

        // 4. 取得相關實體
        Campaign campaign = campaignRepository.findById(request.getCampaignId())
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));
        User reviewer = userRepository.getReferenceById(reviewerId);
        User reviewee = userRepository.findById(request.getRevieweeId())
                .orElseThrow(() -> new RuntimeException("找不到被評價的用戶"));

        // ==========================================
        // 🌟 核心機制：計算分數與 7 天防護網
        // ==========================================

        int scoreChange = 0;
        if (request.getRating() == 5) scoreChange = 1;       // 5星好評 +1
        else if (request.getRating() == 1) scoreChange = -3; // 1星差評 -3 (惡意亂搞重罰)

        boolean isCounted = false;
        String logReason = "收到 " + request.getRating() + " 星評價";

        if (scoreChange > 0) { // 如果是「加分」才需要防護
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            boolean hasRecentPositiveReview = reviewRepository
                    .existsByReviewerIdAndRevieweeIdAndIsScoreCountedTrueAndCreatedAtAfter(
                            reviewerId, reviewee.getId(), sevenDaysAgo);

            if (hasRecentPositiveReview) {
                // 擋下來！分數變動歸零，不發放點數
                scoreChange = 0;
                isCounted = false;
                logReason = "收到 5 星評價 (觸發 7 天防刷單機制，不予加分)";
                log.info("防刷單觸發：User {} 在 7 天內已對 User {} 給過加分評價", reviewerId, reviewee.getId());
            } else {
                isCounted = true; // 放行！
            }
        } else if (scoreChange < 0) {
            isCounted = true; // 負評直接生效，不怕洗負評
        }

        // 5. 建立並儲存評價紀錄 (記得加上 isScoreCounted)
        Review review = Review.builder()
                .campaign(campaign)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .rating(request.getRating())
                .comment(request.getComment())
                .isScoreCounted(isCounted) // 👈 紀錄這筆評價有沒有產生分數
                .build();
        reviewRepository.save(review);

        // 6. 如果分數有實質變動，才去更新會員分數與信用存摺
        if (scoreChange != 0) {
            int newScore = Math.max(0, Math.min(100, reviewee.getCreditScore() + scoreChange));
            reviewee.setCreditScore(newScore);
            userRepository.save(reviewee);

            // 🌟 寫入信用存摺
            CreditScoreLog scoreLog = CreditScoreLog.builder()
                    .user(reviewee)
                    .scoreChange(scoreChange)
                    .reason(logReason)
                    .campaignId(campaign.getId())
                    .build();
            creditScoreLogRepository.save(scoreLog);

            log.info("User {} 信用分數變更 {}。目前信用分數: {}",
                    reviewee.getId(), scoreChange, newScore);
        }
    }

    // 🌟 1. 新增：查詢評價狀態
    @Transactional(readOnly = true)
    public ReviewStatusResponse checkReviewStatus(Long campaignId, Long reviewerId, Long revieweeId) {
        return reviewRepository.findByCampaignIdAndReviewerIdAndRevieweeId(campaignId, reviewerId, revieweeId)
                .map(review -> ReviewStatusResponse.builder()
                        .isReviewed(true)
                        .rating(review.getRating())
                        .comment(review.getComment())
                        .build()
                )
                .orElse(ReviewStatusResponse.builder()
                        .isReviewed(false)
                        .build()
                );
    }

    // 🌟 獲取「別人給我的」評價紀錄看板
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getMyReceivedReviews(Long revieweeId, Pageable pageable) {

        Page<Review> reviews = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(revieweeId, pageable);

        return reviews.map(review -> ReviewResponse.builder()
                .id(review.getId())
                .campaignId(review.getCampaign().getId())
                .campaignName(review.getCampaign().getItemName()) // 假設 Campaign 有這個屬性
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getDisplayName())     // 假設 User 有 name 或 nickname 屬性
                .rating(review.getRating())
                .comment(review.getComment())
                .isScoreCounted(review.getIsScoreCounted())       // 🌟 把防刷單的結果傳給前端
                .createdAt(review.getCreatedAt())
                .build());
    }

}