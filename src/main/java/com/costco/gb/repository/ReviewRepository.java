package com.costco.gb.repository;

import com.costco.gb.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    // 判斷某人是否已經對該單的某人評價過
    boolean existsByCampaignIdAndReviewerIdAndRevieweeId(Long campaignId, Long reviewerId, Long revieweeId);
}
