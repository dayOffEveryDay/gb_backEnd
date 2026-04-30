package com.costco.gb.dto.request;

import lombok.Data;

@Data
public class CreatePurchaseRequestReviewRequest {
    private Integer rating;
    private String comment;
}
