package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PurchaseRequestReviewResponse {
    private Long id;
    private Long purchaseRequestId;
    private String productName;
    private Long reviewerId;
    private String reviewerName;
    private Long revieweeId;
    private String revieweeName;
    private Integer rating;
    private String comment;
    private Boolean isScoreCounted;
    private LocalDateTime createdAt;
}
