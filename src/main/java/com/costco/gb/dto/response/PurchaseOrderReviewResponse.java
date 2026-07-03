package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class PurchaseOrderReviewResponse {
    private Long id;
    private Long orderId;
    private String itemName;
    private Long reviewerId;
    private String reviewerName;
    private Long revieweeId;
    private Integer rating;
    private String comment;
    private Boolean isScoreCounted;
    private LocalDateTime createdAt;
}
