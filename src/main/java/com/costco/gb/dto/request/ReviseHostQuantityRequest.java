package com.costco.gb.dto.request;

import lombok.Data;

@Data
public class ReviseHostQuantityRequest {
    private Integer newProductTotalQuantity; // 修改後的商品總數 (例如發現自己算錯了)
    private Integer newOpenQuantity;         // 修改後願意開放給別人買的數量
}