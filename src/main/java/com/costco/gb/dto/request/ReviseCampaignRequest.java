package com.costco.gb.dto.request;

import lombok.Data;

@Data
public class ReviseCampaignRequest {
    // 這裡代表的是「想要扣除掉」的數量，例如傳入 1 代表少買 1 個
    private Integer quantity;
}