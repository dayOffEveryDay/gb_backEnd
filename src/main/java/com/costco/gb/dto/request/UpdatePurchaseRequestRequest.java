package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UpdatePurchaseRequestRequest {

    private String itemName;
    private String itemDescription;
    private List<String> imageUrls;
    private String rewardType;
    private Integer fixedRewardAmount;
    private String deliveryMethod;
    private LocalDateTime requestExpireTime;
    private Integer deliveryDeadlineHours;
    private Integer minCreditScore;
    private List<@NotNull @Positive Long> selectedProfileIds;

    public boolean hasNonExpiryChanges() {
        return itemName != null || itemDescription != null || imageUrls != null || rewardType != null
                || fixedRewardAmount != null || deliveryMethod != null || deliveryDeadlineHours != null
                || minCreditScore != null || selectedProfileIds != null;
    }
}
