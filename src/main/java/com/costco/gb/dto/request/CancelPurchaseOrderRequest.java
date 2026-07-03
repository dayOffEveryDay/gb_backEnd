package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelPurchaseOrderRequest {

    @NotBlank(message = "取消原因不可空白")
    @Size(max = 100, message = "取消原因不可超過 100 字")
    private String reason;
}
