package com.costco.gb.mapper;

import com.costco.gb.dto.response.CampaignSummaryResponse;
import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.Participant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CampaignMapper {

    // 圖片完整網址使用的服務基底 URL
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // 產生公開列表使用的合購摘要
    public CampaignSummaryResponse toSummaryResponse(Campaign campaign) {

        List<String> imageUrlList = new ArrayList<>();
        if (campaign.getImageUrls() != null && !campaign.getImageUrls().isEmpty()) {
            String[] images = campaign.getImageUrls().split(",");
            for (String img : images) {
                imageUrlList.add(baseUrl + "/images/" + img);
            }
        }

        return CampaignSummaryResponse.builder()
                .id(campaign.getId())
                .itemName(campaign.getItemName())
                .imageUrls(imageUrlList)
                .scenarioType(campaign.getScenarioType())
                .pricePerUnit(campaign.getPricePerUnit())
                .totalQuantity(campaign.getTotalQuantity())
                .availableQuantity(campaign.getAvailableQuantity())
                .meetupLocation(campaign.getMeetupLocation())
                .meetupTime(campaign.getMeetupTime())
                .expireTime(campaign.getExpireTime())
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
    // 產生包含目前使用者參團狀態的合購摘要
    public CampaignSummaryResponse toSummaryResponse(Campaign campaign , Long currentUserId) {

        List<String> imageUrlList = new ArrayList<>();
        if (campaign.getImageUrls() != null && !campaign.getImageUrls().isEmpty()) {
            String[] images = campaign.getImageUrls().split(",");
            for (String img : images) {
                imageUrlList.add(baseUrl + "/images/" + img);
            }
        }
        // 從參與者資料找出目前使用者在此合購單的狀態
        String myStatus = null;
        if (currentUserId != null && campaign.getParticipants() != null) {
            myStatus = campaign.getParticipants().stream()
                    .filter(p -> p.getUser().getId().equals(currentUserId))
                    .map(Participant::getStatus) // 取出 Participant 的 status
                    .findFirst()
                    .orElse(null);
        }

        return CampaignSummaryResponse.builder()
                .id(campaign.getId())
                .itemName(campaign.getItemName())
                .imageUrls(imageUrlList)
                .scenarioType(campaign.getScenarioType())
                .pricePerUnit(campaign.getPricePerUnit())
                .totalQuantity(campaign.getTotalQuantity())
                .availableQuantity(campaign.getAvailableQuantity())
                .meetupLocation(campaign.getMeetupLocation())
                .meetupTime(campaign.getMeetupTime())
                .expireTime(campaign.getExpireTime())
                .status(campaign.getStatus())
                .myParticipantStatus(myStatus)
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
