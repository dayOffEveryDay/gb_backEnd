package com.costco.gb.controller;

import com.costco.gb.dto.request.CreateReviewRequest;
import com.costco.gb.dto.response.ReviewResponse;
import com.costco.gb.dto.response.ReviewStatusResponse;
import com.costco.gb.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<?> createReview(@RequestBody CreateReviewRequest request) {

        // 從 Token 取出當前使用者 ID (評價者)
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long reviewerId = Long.parseLong(userIdStr);

        // 呼叫 Service
        reviewService.createReview(reviewerId, request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "評價成功！感謝您的回饋。"
        ));
    }

    @GetMapping("/check")
    public ResponseEntity<ReviewStatusResponse> checkMyReviewStatus(
            @RequestParam Long campaignId,
            @RequestParam Long revieweeId) {

        // 取得當前使用者 (評價者)
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long currentUserId = Long.parseLong(userIdStr);

        ReviewStatusResponse status = reviewService.checkReviewStatus(campaignId, currentUserId, revieweeId);

        return ResponseEntity.ok(status);
    }

    // 🌟 獲取我的評價看板 (別人給我的)
    @GetMapping("/me/received")
    public ResponseEntity<Page<ReviewResponse>> getMyReceivedReviews(
            @PageableDefault(size = 10) Pageable pageable) {

        // 取出當前登入者 ID
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long currentUserId = Long.parseLong(userIdStr);

        Page<ReviewResponse> responses = reviewService.getMyReceivedReviews(currentUserId, pageable);

        return ResponseEntity.ok(responses);
    }
}