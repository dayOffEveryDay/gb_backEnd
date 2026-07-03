package com.costco.gb.controller;

import com.costco.gb.dto.request.CreatePurchaseOrderReviewRequest;
import com.costco.gb.dto.response.PurchaseOrderReviewResponse;
import com.costco.gb.service.PurchaseOrderReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class PurchaseOrderReviewController {
    private final PurchaseOrderReviewService service;

    @PostMapping("/api/v1/purchase-orders/{orderId}/reviews")
    public ResponseEntity<PurchaseOrderReviewResponse> create(Authentication auth, @PathVariable Long orderId,
                                                               @Valid @RequestBody CreatePurchaseOrderReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(userId(auth), orderId, request));
    }

    @GetMapping("/api/v1/purchase-orders/{orderId}/reviews/status")
    public ResponseEntity<Map<String, Boolean>> status(Authentication auth, @PathVariable Long orderId) {
        return ResponseEntity.ok(Map.of("reviewed", service.status(userId(auth), orderId)));
    }

    @GetMapping("/api/v1/purchase-order-reviews/me/received")
    public ResponseEntity<Page<PurchaseOrderReviewResponse>> received(Authentication auth,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.received(userId(auth), page, size));
    }

    private Long userId(Authentication auth) { return Long.parseLong(auth.getPrincipal().toString()); }
}
