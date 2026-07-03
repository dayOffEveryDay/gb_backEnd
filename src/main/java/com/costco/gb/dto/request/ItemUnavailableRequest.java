package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ItemUnavailableRequest {

    @NotBlank(message = "缺貨原因不可空白")
    @Size(max = 500, message = "缺貨說明不可超過 500 字")
    private String reason;

    private List<String> imageUrls = new ArrayList<>();

    @Size(max = 100, message = "門市名稱不可超過 100 字")
    private String storeName;
}
