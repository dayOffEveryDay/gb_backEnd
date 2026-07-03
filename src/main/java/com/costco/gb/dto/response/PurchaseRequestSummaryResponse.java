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
    private String itemName;
    private List<String> imageUrls;
    private String rewardType;
    private Integer fixedRewardAmount;
    private String requesterCity;
    private String requesterDistrict;
    private String deliveryMethod;
    private String deliveryPublicSummary;
    private LocalDateTime requestExpireTime;
    private Integer deliveryDeadlineHours;
    private Integer minCreditScore;
    private String status;
    private LocalDateTime createdAt;
}
