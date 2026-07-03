package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExtendPurchaseRequestRequest {

    @NotNull(message = "新委託期限不可為空")
    private LocalDateTime requestExpireTime;
}
