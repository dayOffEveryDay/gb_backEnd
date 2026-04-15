package com.costco.gb.repository;

import com.costco.gb.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 1. 舊版防呆：檢查是否已經在「這筆合購單」對「這個人」評價過
    boolean existsByCampaignIdAndReviewerIdAndRevieweeId(Long campaignId, Long reviewerId, Long revieweeId);

    // 2. 🌟 新版防刷單核心：檢查 7 天內是否已經給過「有效加分」的評價
    boolean existsByReviewerIdAndRevieweeIdAndIsScoreCountedTrueAndCreatedAtAfter(
            Long reviewerId, Long revieweeId, LocalDateTime sevenDaysAgo);


    Optional<Review> findByCampaignIdAndReviewerIdAndRevieweeId(Long campaignId, Long reviewerId, Long revieweeId);

    // 🌟 撈出別人給我的評價，依時間倒序
    Page<Review> findByRevieweeIdOrderByCreatedAtDesc(Long revieweeId, Pageable pageable);
}