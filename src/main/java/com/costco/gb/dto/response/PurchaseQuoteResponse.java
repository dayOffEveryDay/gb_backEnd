package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PurchaseQuoteResponse {
    private Long id;
    private Long purchaseRequestId;
    private BigDecimal quoteAmount;
    private String note;
    private String status;
    private PurchaseRequestResponse.UserSummary runner;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
