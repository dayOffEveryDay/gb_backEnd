package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ChatReadRequest {
    @NotNull
    @Positive
    private Long lastReadMessageId;
}
