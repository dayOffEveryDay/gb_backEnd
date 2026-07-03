package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PurchaseQuoteRequest {

    @NotNull(message = "報價金額不可為空")
    @Positive(message = "報價金額必須大於 0")
    private Integer quoteAmount;

    @Size(max = 500, message = "報價備註不可超過 500 字")
    private String quoteNote;
}
