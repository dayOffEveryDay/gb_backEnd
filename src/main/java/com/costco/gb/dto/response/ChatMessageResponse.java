package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageResponse {
    private Long senderId;
    private String avatarUrl;   // 🌟 新增：發送者的頭像網址
    private String senderName;
    private String content;
    private LocalDateTime timestamp;
}
