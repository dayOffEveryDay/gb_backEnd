package com.costco.gb.service;

import com.costco.gb.dto.response.PublicCampaignDetailResponse;
import com.costco.gb.entity.Campaign;
import com.costco.gb.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicCampaignService {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final List<String> NON_PUBLIC_STATUSES = List.of("CANCELLED", "CANCEL_PENDING");

    private final CampaignRepository campaignRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional(readOnly = true)
    public PublicCampaignDetailResponse getCampaign(Long campaignId) {
        Campaign campaign = campaignRepository.findPublicDetailsById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "團購不存在或已刪除"));

        // 已取消或進入取消流程的團購不再公開，但保留 410 讓前端與不存在的資料有所區別。
        if (NON_PUBLIC_STATUSES.contains(campaign.getStatus())) {
            throw new ResponseStatusException(HttpStatus.GONE, "團購已下架、不再公開");
        }

        // 公開端點只組裝分享頁必要資料，不讀取參與者、聊天或登入者個人狀態。
        return PublicCampaignDetailResponse.builder()
                .id(campaign.getId())
                .scenarioType(campaign.getScenarioType())
                .status(campaign.getStatus())
                .itemName(campaign.getItemName())
                .categoryName(campaign.getCategory().getName())
                .availableQuantity(campaign.getAvailableQuantity())
                .totalQuantity(campaign.getTotalQuantity())
                .pricePerUnit(campaign.getPricePerUnit())
                .storeName(campaign.getStore().getName())
                .meetupLocation(campaign.getMeetupLocation())
                .meetupTime(toTaipeiOffset(campaign.getMeetupTime()))
                .expireTime(toTaipeiOffset(campaign.getExpireTime()))
                .imageUrls(toPublicImageUrls(campaign.getImageUrls()))
                .host(PublicCampaignDetailResponse.Host.builder()
                        .id(campaign.getHost().getId())
                        .displayName(campaign.getHost().getDisplayName())
                        .profileImageUrl(campaign.getHost().getProfileImageUrl())
                        .creditScore(campaign.getHost().getCreditScore())
                        .build())
                .canJoin(canJoin(campaign))
                .build();
    }

    private boolean canJoin(Campaign campaign) {
        LocalDateTime now = LocalDateTime.now(TAIPEI_ZONE);
        return "OPEN".equals(campaign.getStatus())
                && campaign.getAvailableQuantity() != null
                && campaign.getAvailableQuantity() > 0
                && (campaign.getExpireTime() == null || campaign.getExpireTime().isAfter(now));
    }

    private OffsetDateTime toTaipeiOffset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(TAIPEI_ZONE).toOffsetDateTime();
    }

    private List<String> toPublicImageUrls(String imageUrls) {
        if (imageUrls == null || imageUrls.isBlank()) {
            return List.of();
        }
        return Arrays.stream(imageUrls.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(this::toPublicImageUrl)
                .toList();
    }

    private String toPublicImageUrl(String imageUrl) {
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (imageUrl.startsWith("/images/")) {
            return normalizedBaseUrl + imageUrl;
        }
        if (imageUrl.startsWith("images/")) {
            return normalizedBaseUrl + "/" + imageUrl;
        }
        return normalizedBaseUrl + "/images/" + imageUrl;
    }
}
