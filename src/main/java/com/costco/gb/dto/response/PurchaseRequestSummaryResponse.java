package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseRequestSummaryResponse {
    private Long id;
    private Long requesterId;
    private String requesterDisplayName;
    private String requesterProfileImageUrl;
    private String itemName;
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
    private LocalDateTime createdAt;
}
