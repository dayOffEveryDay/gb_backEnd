package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class FollowingUserResponse {
    private Long hostId;             // 團主 ID
    private String displayName;      // 團主名字
    private String avatarUrl;        // 團主頭像
    private LocalDateTime followedAt;// 什麼時候追蹤的
}