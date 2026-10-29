package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageResponse {
    private Long messageId;
    private Long senderId;
    // 訊息發送者的頭像網址
    private String avatarUrl;
    private String senderName;
    private String content;
    private LocalDateTime timestamp;
}
