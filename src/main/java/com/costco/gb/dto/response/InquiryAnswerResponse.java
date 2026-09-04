package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InquiryAnswerResponse {
    private Long id;
    private Integer storeId;
    private String storeName;
    private String stockStatus;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private InquiryAuthorResponse author;
}
