package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PurchaseQuoteResponse {
    private Long id;
    private Long purchaseRequestId;
    private String itemName;
    private Long quoterId;
    private String quoterDisplayName;
    private String quoterProfileImageUrl;
    private Integer quoterCreditScore;
    private Integer quoteAmount;
    private String quoteNote;
    private String status;
    private LocalDateTime selectedAt;
    private LocalDateTime withdrawnAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
