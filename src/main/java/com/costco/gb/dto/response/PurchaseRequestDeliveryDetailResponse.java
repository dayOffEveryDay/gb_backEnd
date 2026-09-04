package com.costco.gb.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PurchaseRequestDeliveryDetailResponse {
    private Long id;
    // 僅資料擁有者及已成立訂單雙方可取得來源 ID 與私密欄位。
    private Long sourceProfileId;
    private String city;
    private String district;
    private String locationName;
    private String locationCode;
    private String provider;
    private String address;
    private String recipientName;
    private String recipientPhone;
}
