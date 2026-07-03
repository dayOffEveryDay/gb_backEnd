package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class MeetupRangeRequest {

    @NotBlank(message = "面交地點類型不可空白")
    private String locationType;

    private String city;
    private String district;
    private String locationName;
    private String rangeStart;
    private String rangeEnd;

    @NotBlank(message = "面交顯示文字不可空白")
    private String displayText;

    private Map<String, Object> detail;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
