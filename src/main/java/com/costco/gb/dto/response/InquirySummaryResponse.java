package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InquirySummaryResponse {
    private Long id;
    private String type;
    private String title;
    private String productName;
    private Integer storeId;
    private String storeName;
    private List<String> imageUrls;
    private String status;
    private long answerCount;
    private long followCount;
    private Boolean followed;
    private LocalDateTime createdAt;
    private LocalDateTime expireTime;
}
