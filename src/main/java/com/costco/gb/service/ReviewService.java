package com.costco.gb.service;

import com.costco.gb.dto.request.CreateReviewRequest;
import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.Review;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.ReviewRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;

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

        // 3. 防洗評價：檢查是否已經評價過
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

        // 5. 建立並儲存評價紀錄
        Review review = Review.builder()
                .campaign(campaign)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
        reviewRepository.save(review);

        // 6. 🌟 核心機制：計算並更新被評價者的信用分數
        updateCreditScore(reviewee, request.getRating());

        log.info("User {} 對 User {} 留下了 {} 星評價。目前信用分數: {}",
                reviewerId, reviewee.getId(), request.getRating(), reviewee.getCreditScore());
    }

    // 輔助方法：計算信用分數
    private void updateCreditScore(User user, int rating) {
        int scoreChange = rating - 3; // 5星=+2, 4星=+1, 3星=0, 2星=-1, 1星=-2
        int newScore = user.getCreditScore() + scoreChange;

        // 確保分數在 0 ~ 100 之間
        if (newScore > 100) newScore = 100;
        if (newScore < 0) newScore = 0;

        user.setCreditScore(newScore);
        userRepository.save(user);
    }
}