package com.costco.gb.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdatePurchaseRequestRequest {
    private String productName;
    private String rewardType;
    private BigDecimal fixedRewardAmount;
    private String deliveryMethod;
    private String requestArea;
    private LocalDateTime deadlineAt;
    private String deliveryTimeType;
    private String deliveryTimeNote;
    private Integer minCreditScore;
    private String description;
}
