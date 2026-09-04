package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/** 僅供資料擁有者的常用交付資料 API 回傳，包含聯絡資訊。 */
@Data @Builder
public class UserDeliveryProfileResponse {
    private Long id;
    private String profileType;
    private String label;
    private String city;
    private String district;
    private String locationName;
    private String locationCode;
    private String provider;
    private String address;
    private String recipientName;
    private String recipientPhone;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
