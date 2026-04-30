package com.costco.gb.controller;

import com.costco.gb.dto.request.CreatePurchaseRequestReviewRequest;
import com.costco.gb.dto.response.PurchaseRequestReviewResponse;
import com.costco.gb.dto.response.ReviewStatusResponse;
import com.costco.gb.service.PurchaseRequestReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchase-requests")
@RequiredArgsConstructor
public class PurchaseRequestReviewController {

    private final PurchaseRequestReviewService purchaseRequestReviewService;

    @PostMapping("/{requestId}/reviews")
    public ResponseEntity<PurchaseRequestReviewResponse> createReview(
            @PathVariable Long requestId,
            @RequestBody CreatePurchaseRequestReviewRequest request) {
        return ResponseEntity.ok(purchaseRequestReviewService.createReview(requestId, getCurrentUserId(), request));
    }

    @GetMapping("/{requestId}/reviews/status")
    public ResponseEntity<ReviewStatusResponse> checkReviewStatus(@PathVariable Long requestId) {
        return ResponseEntity.ok(purchaseRequestReviewService.checkReviewStatus(requestId, getCurrentUserId()));
    }

    @GetMapping("/me/received-reviews")
    public ResponseEntity<Page<PurchaseRequestReviewResponse>> getMyReceivedReviews(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(purchaseRequestReviewService.getMyReceivedReviews(getCurrentUserId(), pageable));
    }

    private Long getCurrentUserId() {
        return Long.parseLong((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }
}
