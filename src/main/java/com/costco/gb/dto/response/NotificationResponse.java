package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long id;          // 通知本身的 ID (標記已讀時需要用)
    private String type;      // 通知類型 (例如: CAMPAIGN_FULL)
    private Long referenceId; // 關聯的合購單 ID (前端點擊時才知道要跳去哪個房間)
    private String content;   // 推播文字內容
    private LocalDateTime createdAt; // 通知發生的時間
}