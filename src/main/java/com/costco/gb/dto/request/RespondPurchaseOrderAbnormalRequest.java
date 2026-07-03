package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RespondPurchaseOrderAbnormalRequest {

    @NotBlank(message = "異常回應不可空白")
    @Size(max = 1000, message = "異常回應不可超過 1000 字")
    private String responseText;

    private List<String> imageUrls = new ArrayList<>();
}
