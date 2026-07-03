package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class PurchaseOrderEventResponse {
    private Long id;
    private Long orderId;
    private Long actorId;
    private String actorDisplayName;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private String description;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
