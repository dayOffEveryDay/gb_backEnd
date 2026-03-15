package com.costco.gb.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JoinCampaignRequest {
    @NotNull(message = "數量不能為空")
    @Min(value = 1, message = "認購數量最少為 1")
    private Integer quantity;
}