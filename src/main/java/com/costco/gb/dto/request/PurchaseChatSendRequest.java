package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PurchaseChatSendRequest {
    private String messageType = "TEXT";
    @NotBlank(message = "訊息內容不可空白")
    private String content;
}
