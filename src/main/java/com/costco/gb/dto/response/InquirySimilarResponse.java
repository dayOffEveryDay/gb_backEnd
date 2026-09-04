package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InquirySimilarResponse {
    private Long id;
    private String title;
    private long answerCount;
    private long followCount;
    private LocalDateTime createdAt;
}
