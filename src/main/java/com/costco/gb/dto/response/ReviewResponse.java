package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ReviewResponse {
    private Long id;
    private Long campaignId;
    private String campaignName; // 讓使用者知道是哪一團的評價
    private Long reviewerId;
    private String reviewerName; // 誰給我評價的？
    private Integer rating;      // 星等 1~5
    private String comment;      // 留言內容
    private Boolean isScoreCounted; // 🌟 關鍵：是否有效計分？
    private LocalDateTime createdAt;
}