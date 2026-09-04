package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseQuoteResponse {
    private Long id;
    private Long purchaseRequestId;
    private String itemName;
    private List<String> itemImageUrls;
    private Long quoterId;
    private String requesterDisplayName;
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
