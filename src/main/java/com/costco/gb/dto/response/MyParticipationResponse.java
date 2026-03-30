package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MyParticipationResponse {
    private boolean isHost;    // 🌟 關鍵新增：告訴前端「這個人是不是團主」
    private boolean isJoined;  // 是否已參團
    private Integer quantity;  // 認購的數量 (如果沒參團就是 0)
}