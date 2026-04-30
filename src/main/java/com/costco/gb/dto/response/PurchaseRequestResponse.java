package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseRequestResponse {
    private Long id;
    private String productName;
    private List<String> imageUrls;
    private String rewardType;
    private BigDecimal fixedRewardAmount;
    private long quoteCount;
    private String deliveryMethod;
    private String requestArea;
    private LocalDateTime deadlineAt;
    private String deliveryTimeType;
    private String deliveryTimeNote;
    private Integer minCreditScore;
    private String description;
    private String status;
    private UserSummary requester;
    private UserSummary assignedRunner;
    private Long acceptedQuoteId;
    private Long chatRoomId;
    private Boolean canQuote;
    private Boolean canAcceptDirectly;
    private Boolean canEdit;
    private String actBlockedReason;
    private LocalDateTime deliveredAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class UserSummary {
        private Long id;
        private String displayName;
        private String profileImageUrl;
        private Integer creditScore;
    }
}
