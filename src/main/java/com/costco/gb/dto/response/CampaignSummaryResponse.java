package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CampaignSummaryResponse {
    private Long id;
    private String itemName;
    private List<String> imageUrls;
    private Integer pricePerUnit;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private String meetupLocation;
    private LocalDateTime meetupTime;
    private LocalDateTime expireTime;
    private String status;
    private String scenarioType;

    // 關聯資訊 (扁平化處理，方便前端直接讀取)
    private String storeName;
    private String categoryName;

    // 團主資訊 (前端需要顯示頭像和信用評分)
    private HostSummary host;
    // 🌟 新增：當前登入者在這張單裡的專屬狀態 (JOINED, NO_SHOW, DISPUTED, COMPLETED 等)
    // 如果是訪客在大廳看，這個欄位就會是 null
    private String myParticipantStatus;
    @Data
    @Builder
    public static class HostSummary {
        private Long id;
        private String displayName;
        private String profileImageUrl;
        private Integer creditScore;
    }
}