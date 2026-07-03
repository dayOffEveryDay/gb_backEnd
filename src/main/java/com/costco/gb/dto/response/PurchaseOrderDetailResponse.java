package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private Long runnerId;
    private String runnerDisplayName;
    private Long selectedQuoteId;
    private String rewardType;
    private Integer rewardAmount;
    private String deliveryMethod;
    private String deliveryPublicSummary;
    private Map<String, Object> deliveryPrivateDetail;
    private List<MeetupRangeResponse> meetupRanges;
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
