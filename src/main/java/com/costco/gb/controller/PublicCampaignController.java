package com.costco.gb.controller;

import com.costco.gb.dto.response.PublicCampaignDetailResponse;
import com.costco.gb.service.PublicCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/campaigns")
@RequiredArgsConstructor
public class PublicCampaignController {

    private final PublicCampaignService publicCampaignService;

    @GetMapping("/{campaignId}")
    public ResponseEntity<PublicCampaignDetailResponse> getCampaign(@PathVariable Long campaignId) {
        return ResponseEntity.ok(publicCampaignService.getCampaign(campaignId));
    }
}
