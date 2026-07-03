package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class UserDeliveryProfileRequest {

    @NotBlank(message = "交付資料類型不可空白")
    private String profileType;

    @NotBlank(message = "地點類型不可空白")
    private String locationType;

    private String label;
    private String city;
    private String district;
    private String locationName;
    private String rangeStart;
    private String rangeEnd;

    @NotBlank(message = "顯示文字不可空白")
    private String displayText;

    private Map<String, Object> detail;
    private Boolean isDefault;
}
