package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageResponse {
    private Long senderId;
    private String senderName;
    private String content;
    private LocalDateTime timestamp;
}
