package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserDeliveryProfileRequest {
    @NotBlank(message = "交付資料類型不可空白")
    @Size(max = 30)
    private String profileType;
    @Size(max = 100)
    private String label;
    @Size(max = 50)
    private String city;
    @Size(max = 50)
    private String district;
    @Size(max = 255)
    private String locationName;
    @Size(max = 100)
    private String locationCode;
    @Size(max = 50)
    private String provider;
    @Size(max = 255)
    private String address;
    @Size(max = 100)
    private String recipientName;
    @Size(max = 30)
    private String recipientPhone;
    private Boolean isDefault;
}
