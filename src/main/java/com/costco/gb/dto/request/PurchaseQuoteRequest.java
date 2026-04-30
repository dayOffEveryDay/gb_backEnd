package com.costco.gb.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseQuoteRequest {
    private BigDecimal quoteAmount;
    private String note;
}
