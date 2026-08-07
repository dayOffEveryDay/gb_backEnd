package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PurchaseOrderResponse {
    private Long id;
    private Long purchaseRequestId;
    private String itemName;
    private Long requesterId;
    private String requesterDisplayName;
    private String requesterProfileImageUrl;
    private Long runnerId;
    private String runnerDisplayName;
    private String runnerProfileImageUrl;
    private Long selectedQuoteId;
    private Long chatRoomId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
