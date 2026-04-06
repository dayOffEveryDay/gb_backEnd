package com.costco.gb.mapper;

import com.costco.gb.dto.response.CampaignSummaryResponse;
import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.Participant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component // 🌟 交給 Spring 管理，這樣才能注入 baseUrl
public class CampaignMapper {

    // 這裡請換成你原本寫在 CampaignService 裡的 baseUrl 變數
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // 🌟 1. 給大廳用的舊方法 (維持不變，直接呼叫新方法並傳入 null)
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
                .imageUrls(imageUrlList) // 👈 改成塞入我們剛組裝好的 List
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
    // 🌟 2. 給「我的跟團」用的新方法 (傳入 currentUserId 來比對狀態)
    public CampaignSummaryResponse toSummaryResponse(Campaign campaign , Long currentUserId) {

        List<String> imageUrlList = new ArrayList<>();
        if (campaign.getImageUrls() != null && !campaign.getImageUrls().isEmpty()) {
            String[] images = campaign.getImageUrls().split(",");
            for (String img : images) {
                imageUrlList.add(baseUrl + "/images/" + img);
            }
        }
        // 🔍 找出當前使用者在這張單的專屬狀態
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
                .imageUrls(imageUrlList) // 👈 改成塞入我們剛組裝好的 List
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