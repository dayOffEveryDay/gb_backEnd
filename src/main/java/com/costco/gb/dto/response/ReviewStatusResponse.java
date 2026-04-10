package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewStatusResponse {
    private boolean isReviewed; // 是否已經評價過？
    private Integer rating;     // 如果有評過，當初給幾顆星？(若未評則為 null)
    private String comment;     // 如果有評過，當初留了什麼言？(若未評則為 null)
}