package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateInquiryRequest {

    @NotBlank(message = "問題類型不可空白")
    private String type;

    @NotBlank(message = "問題標題不可空白")
    @Size(max = 255, message = "問題標題不可超過 255 個字元")
    private String title;

    @Size(max = 255, message = "商品名稱不可超過 255 個字元")
    private String productName;

    private Integer storeId;

    private String content;
}
