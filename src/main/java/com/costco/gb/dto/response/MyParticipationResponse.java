package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MyParticipationResponse {
    // 目前使用者是否為此合購單團主
    private boolean isHost;
    private boolean isJoined;  // 是否已參團
    private Integer quantity;  // 認購的數量 (如果沒參團就是 0)
}
