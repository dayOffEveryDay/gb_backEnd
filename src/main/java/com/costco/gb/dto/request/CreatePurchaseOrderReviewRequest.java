package com.costco.gb.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreatePurchaseOrderReviewRequest {
    @NotNull @Min(1) @Max(5) private Integer rating;
    @Size(max = 255) private String comment;
}
