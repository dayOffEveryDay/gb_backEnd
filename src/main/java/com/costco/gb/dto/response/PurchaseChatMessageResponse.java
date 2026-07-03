package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class PurchaseChatMessageResponse {
    private Long id;
    private Long chatRoomId;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String messageType;
    private String content;
    private LocalDateTime createdAt;
}
