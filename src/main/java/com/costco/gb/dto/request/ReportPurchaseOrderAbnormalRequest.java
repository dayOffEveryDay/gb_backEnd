package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReportPurchaseOrderAbnormalRequest {

    @NotBlank(message = "異常類型不可空白")
    private String abnormalType;

    @NotBlank(message = "異常說明不可空白")
    @Size(max = 1000, message = "異常說明不可超過 1000 字")
    private String description;

    private List<String> imageUrls = new ArrayList<>();
}
