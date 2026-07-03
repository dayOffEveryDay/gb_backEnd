package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShipPurchaseOrderRequest {

    @NotBlank(message = "物流公司不可空白")
    @Size(max = 50, message = "物流公司不可超過 50 字")
    private String logisticsProvider;

    @NotBlank(message = "物流單號不可空白")
    @Size(max = 100, message = "物流單號不可超過 100 字")
    private String trackingNumber;

    @Size(max = 500, message = "物流查詢網址不可超過 500 字")
    private String trackingUrl;
}
