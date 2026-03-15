package com.costco.gb.dto.request;

import lombok.Data;

@Data
public class CreateReviewRequest {
    private Long campaignId; // 發生交易的合購單
    private Long revieweeId; // 被評價的人 (如果我是團主，這就是團員ID；如果我是團員，這就是團主ID)
    private Integer rating;  // 1 ~ 5
    private String comment;  // 評價留言
}