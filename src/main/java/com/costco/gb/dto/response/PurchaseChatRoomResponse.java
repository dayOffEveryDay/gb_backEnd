package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class PurchaseChatRoomResponse {
    private Long id;
    private Long orderId;
    private String itemName;
    private Long counterpartId;
    private String counterpartName;
    private String counterpartAvatarUrl;
    private String orderStatus;
    private boolean readOnly;
    private long unreadCount;
    private PurchaseChatMessageResponse lastMessage;
    private LocalDateTime updatedAt;
}
