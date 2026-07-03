package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private String requesterCity;
    private String requesterDistrict;
    private String deliveryMethod;
    private String deliveryPublicSummary;
    private Map<String, Object> deliveryPrivateDetail;
    private List<MeetupRangeResponse> meetupRanges;
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
