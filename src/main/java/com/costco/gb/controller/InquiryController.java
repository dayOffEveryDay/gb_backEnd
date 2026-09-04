package com.costco.gb.controller;

import com.costco.gb.dto.request.CreateInquiryAnswerRequest;
import com.costco.gb.dto.request.CreateInquiryRequest;
import com.costco.gb.dto.response.InquiryAnswerResponse;
import com.costco.gb.dto.response.InquiryDetailResponse;
import com.costco.gb.dto.response.InquirySimilarResponse;
import com.costco.gb.dto.response.InquirySummaryResponse;
import com.costco.gb.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/inquiries", "/api/v1/inquiries"})
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @GetMapping
    public ResponseEntity<Page<InquirySummaryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ResponseEntity.ok(inquiryService.list(keyword, type, storeId, page, size,
                optionalUserId(authentication)));
    }

    @GetMapping("/similar")
    public ResponseEntity<List<InquirySimilarResponse>> findSimilar(
            @RequestParam String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer storeId) {
        return ResponseEntity.ok(inquiryService.findSimilar(keyword, type, storeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InquiryDetailResponse> getDetail(@PathVariable Long id,
                                                            Authentication authentication) {
        return ResponseEntity.ok(inquiryService.getDetail(id, optionalUserId(authentication)));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<InquiryDetailResponse> create(
            Authentication authentication,
            @Valid @RequestPart("request") CreateInquiryRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        InquiryDetailResponse response = inquiryService.create(currentUserId(authentication), request, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/answers")
    public ResponseEntity<InquiryAnswerResponse> createAnswer(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CreateInquiryAnswerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                inquiryService.createAnswer(currentUserId(authentication), id, request));
    }

    @PutMapping("/{inquiryId}/answers/{answerId}")
    public ResponseEntity<InquiryAnswerResponse> updateAnswer(
            Authentication authentication,
            @PathVariable Long inquiryId,
            @PathVariable Long answerId,
            @Valid @RequestBody CreateInquiryAnswerRequest request) {
        return ResponseEntity.ok(inquiryService.updateAnswer(
                currentUserId(authentication), inquiryId, answerId, request));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<Map<String, Boolean>> follow(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("followed", inquiryService.follow(currentUserId(authentication), id)));
    }

    @DeleteMapping("/{id}/follow")
    public ResponseEntity<Map<String, Boolean>> unfollow(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("followed", inquiryService.unfollow(currentUserId(authentication), id)));
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
