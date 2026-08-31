package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class PublicCampaignDetailResponse {
    private Long id;
    private String scenarioType;
    private String status;
    private String itemName;
    private String categoryName;
    private Integer availableQuantity;
    private Integer totalQuantity;
    private Integer pricePerUnit;
    private String storeName;
    private String meetupLocation;
    private OffsetDateTime meetupTime;
    private OffsetDateTime expireTime;
    private List<String> imageUrls;
    private Host host;
    private boolean canJoin;

    @Data
    @Builder
    public static class Host {
        private Long id;
        private String displayName;
        private String profileImageUrl;
        private Integer creditScore;
    }
}
