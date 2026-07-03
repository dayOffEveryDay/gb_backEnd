package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseOrderSummaryResponse {
    private Long id;
    private Long purchaseRequestId;
    private Long chatRoomId;
    private String itemName;
    private List<String> imageUrls;
    private String role;
    private Long requesterId;
    private String requesterDisplayName;
    private Long runnerId;
    private String runnerDisplayName;
    private String deliveryMethod;
    private String rewardType;
    private Integer rewardAmount;
    private String status;
    private LocalDateTime deliveryDeadlineAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
