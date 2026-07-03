package com.costco.gb.dto.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class UpdatePurchaseRequestRequest {

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
    private LocalDateTime requestExpireTime;
    private Integer deliveryDeadlineHours;
    private Integer minCreditScore;
    private List<Long> selectedProfileIds;

    @Valid
    private List<MeetupRangeRequest> manualMeetupRanges;

    private Boolean saveManualToProfile;
    private Boolean saveDeliveryToProfile;

    public boolean hasNonExpiryChanges() {
        return itemName != null || itemDescription != null || imageUrls != null || rewardType != null
                || fixedRewardAmount != null || requesterCity != null || requesterDistrict != null
                || deliveryMethod != null || deliveryPublicSummary != null || deliveryPrivateDetail != null
                || deliveryDeadlineHours != null || minCreditScore != null || selectedProfileIds != null
                || manualMeetupRanges != null || Boolean.TRUE.equals(saveManualToProfile)
                || Boolean.TRUE.equals(saveDeliveryToProfile);
    }
}
