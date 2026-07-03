package com.costco.gb.controller;

import com.costco.gb.dto.request.PurchaseQuoteRequest;
import com.costco.gb.dto.response.PurchaseOrderResponse;
import com.costco.gb.dto.response.PurchaseQuoteResponse;
import com.costco.gb.service.PurchaseQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchase-quotes")
@RequiredArgsConstructor
public class PurchaseQuoteController {

    private final PurchaseQuoteService purchaseQuoteService;

    @GetMapping("/me")
    public ResponseEntity<Page<PurchaseQuoteResponse>> getMyQuotes(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(purchaseQuoteService.getMyQuotes(currentUserId(authentication), page, size));
    }

    @PutMapping("/{quoteId}")
    public ResponseEntity<PurchaseQuoteResponse> updateQuote(
            Authentication authentication,
            @PathVariable Long quoteId,
            @Valid @RequestBody PurchaseQuoteRequest request
    ) {
        return ResponseEntity.ok(
                purchaseQuoteService.updateQuote(currentUserId(authentication), quoteId, request)
        );
    }

    @PostMapping("/{quoteId}/withdraw")
    public ResponseEntity<PurchaseQuoteResponse> withdrawQuote(
            Authentication authentication,
            @PathVariable Long quoteId
    ) {
        return ResponseEntity.ok(purchaseQuoteService.withdrawQuote(currentUserId(authentication), quoteId));
    }

    @PostMapping("/{quoteId}/select")
    public ResponseEntity<PurchaseOrderResponse> selectQuote(
            Authentication authentication,
            @PathVariable Long quoteId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                purchaseQuoteService.selectQuote(currentUserId(authentication), quoteId)
        );
    }

    private Long currentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getPrincipal().toString());
    }
}
