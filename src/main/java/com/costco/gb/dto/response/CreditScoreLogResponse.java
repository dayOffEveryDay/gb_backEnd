package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CreditScoreLogResponse {
    private Long id;
    private Integer scoreChange; // 異動分數
    private String reason;       // 異動原因
    private Long campaignId;     // 相關合購單 (供前端跳轉)
    private Long purchaseRequestId;
    private String sourceType;
    private LocalDateTime createdAt;
}
