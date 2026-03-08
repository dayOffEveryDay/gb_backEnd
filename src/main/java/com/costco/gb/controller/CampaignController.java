package com.costco.gb.controller;

import com.costco.gb.dto.response.CampaignSummaryResponse;
import com.costco.gb.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    public ResponseEntity<Page<CampaignSummaryResponse>> getCampaigns(
            @RequestParam(required = false) Integer storeId,
            @RequestParam(defaultValue = "0") int page,   // 預設第 0 頁
            @RequestParam(defaultValue = "10") int size   // 預設每頁 10 筆
    ) {
        Page<CampaignSummaryResponse> response = campaignService.getActiveCampaigns(storeId, page, size);
        return ResponseEntity.ok(response);
    }
}