package com.costco.gb.service;

import com.costco.gb.dto.response.CampaignSummaryResponse;
import com.costco.gb.entity.Campaign;
import com.costco.gb.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;

    public Page<CampaignSummaryResponse> getActiveCampaigns(Integer storeId, int page, int size) {

        // 1. 設定分頁與排序 (依建立時間倒序，越新的在越上面)
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        LocalDateTime now = LocalDateTime.now();
        Page<Campaign> campaignPage;

        // 2. 判斷是否有傳入門市 ID 進行過濾
        if (storeId != null) {
            campaignPage = campaignRepository.findByStoreIdAndStatusAndExpireTimeAfter(
                    storeId, "OPEN", now, pageable);
        } else {
            campaignPage = campaignRepository.findByStatusAndExpireTimeAfter(
                    "OPEN", now, pageable);
        }

        // 3. 將 Entity 轉換為 DTO 回傳
        return campaignPage.map(this::mapToSummaryResponse);
    }

    // 輔助方法：Entity 轉 DTO
    private CampaignSummaryResponse mapToSummaryResponse(Campaign campaign) {
        return CampaignSummaryResponse.builder()
                .id(campaign.getId())
                .itemName(campaign.getItemName())
                .itemImageUrl(campaign.getItemImageUrl())
                .pricePerUnit(campaign.getPricePerUnit())
                .totalQuantity(campaign.getTotalQuantity())
                .availableQuantity(campaign.getAvailableQuantity())
                .meetupLocation(campaign.getMeetupLocation())
                .meetupTime(campaign.getMeetupTime())
                .status(campaign.getStatus())
                .storeName(campaign.getStore().getName())
                .categoryName(campaign.getCategory().getName())
                .host(CampaignSummaryResponse.HostSummary.builder()
                        .id(campaign.getHost().getId())
                        .displayName(campaign.getHost().getDisplayName())
                        .profileImageUrl(campaign.getHost().getProfileImageUrl())
                        .creditScore(campaign.getHost().getCreditScore())
                        .build())
                .build();
    }
}