package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseRequestDetailResponse {
    private Long id;
    private Long requesterId;
    private String requesterDisplayName;
    private String requesterProfileImageUrl;
    private Integer requesterCreditScore;
    private String itemName;
    private String itemDescription;
    private List<String> imageUrls;
    private String rewardType;
    private Integer fixedRewardAmount;
    private String deliveryMethod;
    // 由交付快照計算的公開摘要，不是 purchase_requests 資料庫欄位。
    private String deliveryPublicSummary;
    private List<PurchaseRequestDeliveryDetailResponse> deliveryDetails;
    private LocalDateTime requestExpireTime;
    private Integer deliveryDeadlineHours;
    private Integer minCreditScore;
    private String status;
    private Integer reopenCount;
    private LocalDateTime cancelledAt;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
