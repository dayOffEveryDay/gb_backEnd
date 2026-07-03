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
    private Long runnerId;
    private String runnerDisplayName;
    private Long selectedQuoteId;
    private Long chatRoomId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
