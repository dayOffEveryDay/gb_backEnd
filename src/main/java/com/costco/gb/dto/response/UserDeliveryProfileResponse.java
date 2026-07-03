package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class UserDeliveryProfileResponse {
    private Long id;
    private String profileType;
    private String locationType;
    private String label;
    private String city;
    private String district;
    private String locationName;
    private String rangeStart;
    private String rangeEnd;
    private String displayText;
    private Map<String, Object> detail;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
