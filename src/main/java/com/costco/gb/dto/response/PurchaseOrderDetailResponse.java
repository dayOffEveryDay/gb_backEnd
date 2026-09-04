package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseOrderDetailResponse {
    private Long id;
    private Long purchaseRequestId;
    private Long chatRoomId;
    private String itemName;
    private String itemDescription;
    private List<String> imageUrls;
    private Long requesterId;
    private String requesterDisplayName;
    private String requesterProfileImageUrl;
    private Long runnerId;
    private String runnerDisplayName;
    private String runnerProfileImageUrl;
    private Long selectedQuoteId;
    private String rewardType;
    private Integer rewardAmount;
    private String deliveryMethod;
    // 由交付快照計算的公開摘要，不是 purchase_requests 資料庫欄位。
    private String deliveryPublicSummary;
    private List<PurchaseRequestDeliveryDetailResponse> deliveryDetails;
    private String status;
    private LocalDateTime requesterConfirmedAt;
    private LocalDateTime runnerConfirmedAt;
    private LocalDateTime startedAt;
    private String logisticsProvider;
    private String trackingNumber;
    private String trackingUrl;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Long blameUserId;
    private Integer creditPenalty;
    private Long abnormalReporterId;
    private Long abnormalReportedUserId;
    private String abnormalType;
    private LocalDateTime abnormalReportedAt;
    private LocalDateTime abnormalRespondedAt;
    private LocalDateTime deliveryDeadlineAt;
    private List<String> availableActions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
