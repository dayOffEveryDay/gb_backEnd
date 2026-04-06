package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class BlockedUserResponse {
    private Long userId;             // 解除封鎖時需要用到他的 ID
    private String displayName;      // 名字
    private String avatarUrl;        // 頭像
    private LocalDateTime blockedAt; // 什麼時候加入黑名單的 (對應 Block 實體的 createdAt)
}