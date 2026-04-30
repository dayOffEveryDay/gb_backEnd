package com.costco.gb.controller;

import com.costco.gb.dto.request.CancelPurchaseRequestRequest;
import com.costco.gb.dto.request.CreatePurchaseRequestRequest;
import com.costco.gb.dto.request.PurchaseQuoteRequest;
import com.costco.gb.dto.request.UpdateImageOrderRequest;
import com.costco.gb.dto.request.UpdatePurchaseRequestRequest;
import com.costco.gb.dto.response.PurchaseQuoteResponse;
import com.costco.gb.dto.response.PurchaseRequestResponse;
import com.costco.gb.service.PurchaseRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/purchase-requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final PurchaseRequestService purchaseRequestService;

    @GetMapping
    public ResponseEntity<Page<PurchaseRequestResponse>> getPurchaseRequests(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String rewardType,
            @RequestParam(required = false) String deliveryMethod,
            @RequestParam(required = false) String requestArea,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<PurchaseRequestResponse> response = purchaseRequestService.findPublic(
                keyword, rewardType, deliveryMethod, requestArea, status, getOptionalCurrentUserId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<PurchaseRequestResponse> getPurchaseRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(purchaseRequestService.getDetail(requestId, getOptionalCurrentUserId()));
    }

    @PostMapping
    public ResponseEntity<PurchaseRequestResponse> createPurchaseRequest(@ModelAttribute CreatePurchaseRequestRequest request) {
        return ResponseEntity.ok(purchaseRequestService.create(getCurrentUserId(), request));
    }

    @PutMapping("/{requestId}")
    public ResponseEntity<PurchaseRequestResponse> updatePurchaseRequest(
            @PathVariable Long requestId,
            @RequestBody UpdatePurchaseRequestRequest request) {
        return ResponseEntity.ok(purchaseRequestService.update(requestId, getCurrentUserId(), request));
    }

    @PostMapping("/{requestId}/images")
    public ResponseEntity<PurchaseRequestResponse> uploadImages(
            @PathVariable Long requestId,
            @RequestParam("images") List<MultipartFile> images) {
        return ResponseEntity.ok(purchaseRequestService.uploadImages(requestId, getCurrentUserId(), images));
    }

    @PutMapping("/{requestId}/images/order")
    public ResponseEntity<PurchaseRequestResponse> updateImageOrder(
            @PathVariable Long requestId,
            @RequestBody UpdateImageOrderRequest request) {
        return ResponseEntity.ok(purchaseRequestService.updateImageOrder(requestId, getCurrentUserId(), request.getImageUrls()));
    }

    @DeleteMapping("/{requestId}/images/{fileName}")
    public ResponseEntity<PurchaseRequestResponse> deleteImage(
            @PathVariable Long requestId,
            @PathVariable String fileName) {
        return ResponseEntity.ok(purchaseRequestService.deleteImage(requestId, getCurrentUserId(), fileName));
    }

    @PostMapping("/{requestId}/accept")
    public ResponseEntity<PurchaseRequestResponse> acceptDirectly(@PathVariable Long requestId) {
        return ResponseEntity.ok(purchaseRequestService.acceptDirectly(requestId, getCurrentUserId()));
    }

    @PostMapping("/{requestId}/quotes")
    public ResponseEntity<PurchaseQuoteResponse> createQuote(
            @PathVariable Long requestId,
            @RequestBody PurchaseQuoteRequest request) {
        return ResponseEntity.ok(purchaseRequestService.createQuote(requestId, getCurrentUserId(), request));
    }

    @PutMapping("/{requestId}/quotes/{quoteId}")
    public ResponseEntity<PurchaseQuoteResponse> updateQuote(
            @PathVariable Long requestId,
            @PathVariable Long quoteId,
            @RequestBody PurchaseQuoteRequest request) {
        return ResponseEntity.ok(purchaseRequestService.updateQuote(requestId, quoteId, getCurrentUserId(), request));
    }

    @PostMapping("/{requestId}/quotes/{quoteId}/cancel")
    public ResponseEntity<PurchaseQuoteResponse> cancelQuote(
            @PathVariable Long requestId,
            @PathVariable Long quoteId) {
        return ResponseEntity.ok(purchaseRequestService.cancelQuote(requestId, quoteId, getCurrentUserId()));
    }

    @GetMapping("/{requestId}/quotes")
    public ResponseEntity<List<PurchaseQuoteResponse>> getQuotes(@PathVariable Long requestId) {
        return ResponseEntity.ok(purchaseRequestService.getQuotes(requestId, getCurrentUserId()));
    }

    @GetMapping("/{requestId}/quotes/me")
    public ResponseEntity<PurchaseQuoteResponse> getMyQuote(@PathVariable Long requestId) {
        return ResponseEntity.ok(purchaseRequestService.getMyQuote(requestId, getCurrentUserId()));
    }

    @PostMapping("/{requestId}/quotes/{quoteId}/accept")
    public ResponseEntity<PurchaseRequestResponse> acceptQuote(
            @PathVariable Long requestId,
            @PathVariable Long quoteId) {
        return ResponseEntity.ok(purchaseRequestService.acceptQuote(requestId, quoteId, getCurrentUserId()));
    }

    @PostMapping("/{requestId}/deliver")
    public ResponseEntity<PurchaseRequestResponse> deliver(@PathVariable Long requestId) {
        return ResponseEntity.ok(purchaseRequestService.deliver(requestId, getCurrentUserId()));
    }

    @PostMapping("/{requestId}/complete")
    public ResponseEntity<PurchaseRequestResponse> complete(@PathVariable Long requestId) {
        return ResponseEntity.ok(purchaseRequestService.complete(requestId, getCurrentUserId()));
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<PurchaseRequestResponse> cancel(
            @PathVariable Long requestId,
            @RequestBody(required = false) CancelPurchaseRequestRequest request) {
        return ResponseEntity.ok(purchaseRequestService.cancel(requestId, getCurrentUserId(), request));
    }

    @GetMapping("/my-created")
    public ResponseEntity<Page<PurchaseRequestResponse>> getMyCreated(
            @PageableDefault(page = 0, size = 10) Pageable pageable) {
        return ResponseEntity.ok(purchaseRequestService.getMyCreated(getCurrentUserId(), pageable));
    }

    @GetMapping("/my-assigned")
    public ResponseEntity<Page<PurchaseRequestResponse>> getMyAssigned(
            @PageableDefault(page = 0, size = 10) Pageable pageable) {
        return ResponseEntity.ok(purchaseRequestService.getMyAssigned(getCurrentUserId(), pageable));
    }

    @GetMapping("/my-quotes")
    public ResponseEntity<Page<PurchaseRequestResponse>> getMyQuoted(
            @PageableDefault(page = 0, size = 10) Pageable pageable) {
        return ResponseEntity.ok(purchaseRequestService.getMyQuoted(getCurrentUserId(), pageable));
    }

    private Long getCurrentUserId() {
        return Long.parseLong((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    private Long getOptionalCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof String principal
                && !"anonymousUser".equals(principal)) {
            return Long.parseLong(principal);
        }
        return null;
    }
}
