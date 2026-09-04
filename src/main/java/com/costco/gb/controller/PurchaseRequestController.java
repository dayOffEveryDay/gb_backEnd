package com.costco.gb.controller;

import com.costco.gb.dto.request.CreatePurchaseRequestRequest;
import com.costco.gb.dto.request.ExtendPurchaseRequestRequest;
import com.costco.gb.dto.request.PurchaseQuoteRequest;
import com.costco.gb.dto.request.UpdatePurchaseRequestRequest;
import com.costco.gb.dto.response.PurchaseOrderResponse;
import com.costco.gb.dto.response.PurchaseQuoteResponse;
import com.costco.gb.dto.response.PurchaseRequestDetailResponse;
import com.costco.gb.dto.response.PurchaseRequestSummaryResponse;
import com.costco.gb.service.PurchaseRequestService;
import com.costco.gb.service.PurchaseQuoteService;
import com.costco.gb.service.PurchaseRequestImageService;
import com.costco.gb.dto.request.UpdateImageOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/v1/purchase-requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final PurchaseRequestService purchaseRequestService;
    private final PurchaseQuoteService purchaseQuoteService;
    private final PurchaseRequestImageService purchaseRequestImageService;

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(
            Authentication authentication,
            @Valid @RequestBody CreatePurchaseRequestRequest request
    ) {
        Long requestId = purchaseRequestService.create(currentUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", requestId));
    }

    @GetMapping
    public ResponseEntity<Page<PurchaseRequestSummaryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String deliveryMethod,
            @RequestParam(required = false) String rewardType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(purchaseRequestService.list(
                status, city, district, deliveryMethod, rewardType, keyword, page, size
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseRequestDetailResponse> getDetail(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(purchaseRequestService.getDetail(id, optionalUserId(authentication)));
    }

    @GetMapping("/my-created")
    public ResponseEntity<Page<PurchaseRequestSummaryResponse>> getMyCreated(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(purchaseRequestService.listMyCreated(
                currentUserId(authentication), status, page, size
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseRequestDetailResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePurchaseRequestRequest request
    ) {
        return ResponseEntity.ok(purchaseRequestService.update(currentUserId(authentication), id, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(Authentication authentication, @PathVariable Long id) {
        purchaseRequestService.cancel(currentUserId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/extend")
    public ResponseEntity<PurchaseRequestDetailResponse> extend(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody ExtendPurchaseRequestRequest request
    ) {
        return ResponseEntity.ok(purchaseRequestService.extend(
                currentUserId(authentication), id, request.getRequestExpireTime()
        ));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<PurchaseRequestDetailResponse> reopen(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(purchaseRequestService.reopen(currentUserId(authentication), id));
    }

    @PostMapping("/{id}/quotes")
    public ResponseEntity<PurchaseQuoteResponse> createQuote(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PurchaseQuoteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                purchaseQuoteService.createQuote(currentUserId(authentication), id, request)
        );
    }

    @GetMapping("/{id}/quotes")
    public ResponseEntity<List<PurchaseQuoteResponse>> getQuotes(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(purchaseQuoteService.getRequestQuotes(currentUserId(authentication), id));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<PurchaseOrderResponse> acceptFixedRequest(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                purchaseQuoteService.acceptFixedRequest(currentUserId(authentication), id)
        );
    }

    @PostMapping(value = "/{id}/images", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, List<String>>> uploadImages(
            Authentication authentication, @PathVariable Long id,
            @RequestParam("images") List<MultipartFile> images) {
        return ResponseEntity.ok(Map.of("imageUrls", purchaseRequestImageService.upload(
                currentUserId(authentication), id, images)));
    }

    @DeleteMapping("/{id}/images/{fileName}")
    public ResponseEntity<Map<String, List<String>>> deleteImage(
            Authentication authentication, @PathVariable Long id, @PathVariable String fileName) {
        return ResponseEntity.ok(Map.of("imageUrls", purchaseRequestImageService.delete(
                currentUserId(authentication), id, fileName)));
    }

    @PutMapping("/{id}/images/order")
    public ResponseEntity<Map<String, List<String>>> reorderImages(
            Authentication authentication, @PathVariable Long id,
            @RequestBody UpdateImageOrderRequest request) {
        return ResponseEntity.ok(Map.of("imageUrls", purchaseRequestImageService.reorder(
                currentUserId(authentication), id, request.getImageUrls())));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getPrincipal().toString());
    }

    private Long optionalUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return currentUserId(authentication);
    }
}
