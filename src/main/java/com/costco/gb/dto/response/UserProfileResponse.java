package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserProfileResponse {
    private Long userId;
    private String displayName;
    private String avatarUrl;        // 頭像 (對應 DB 的 profile_image_url)
    private Integer creditScore;     // 信用分數
    private Integer totalHostedCount;// 歷史總開團數
    private Integer totalJoinedCount;// 歷史總參團數
    private LocalDateTime joinDate;  // 加入平台時間 (對應 created_at)

    // 🌟 他現在正在開的團 (讓前端可以直接渲染出卡片列表)
    private List<CampaignSummaryResponse> activeCampaigns;
}