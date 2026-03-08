package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CampaignSummaryResponse {
    private Long id;
    private String itemName;
    private String itemImageUrl;
    private Integer pricePerUnit;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private String meetupLocation;
    private LocalDateTime meetupTime;
    private String status;

    // 關聯資訊 (扁平化處理，方便前端直接讀取)
    private String storeName;
    private String categoryName;

    // 團主資訊 (前端需要顯示頭像和信用評分)
    private HostSummary host;

    @Data
    @Builder
    public static class HostSummary {
        private Long id;
        private String displayName;
        private String profileImageUrl;
        private Integer creditScore;
    }
}