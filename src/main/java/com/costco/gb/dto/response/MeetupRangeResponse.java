package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class MeetupRangeResponse {
    private Long id;
    private Long sourceProfileId;
    private String locationType;
    private String city;
    private String district;
    private String locationName;
    private String rangeStart;
    private String rangeEnd;
    private String displayText;
    private Map<String, Object> detail;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
