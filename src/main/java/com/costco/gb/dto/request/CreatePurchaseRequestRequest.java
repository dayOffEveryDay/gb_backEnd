package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CreatePurchaseRequestRequest {

    @NotBlank(message = "商品名稱不可空白")
    private String itemName;

    private String itemDescription;
    private List<String> imageUrls = new ArrayList<>();

    @NotBlank(message = "酬金模式不可空白")
    private String rewardType;

    private Integer fixedRewardAmount;

    @NotBlank(message = "交付方式不可空白")
    private String deliveryMethod;

    @NotNull(message = "委託期限不可為空")
    private LocalDateTime requestExpireTime;

    @NotNull(message = "交貨期限時數不可為空")
    private Integer deliveryDeadlineHours;

    private Integer minCreditScore = 0;

    @NotEmpty(message = "請選擇至少一筆常用交付資料")
    private List<@NotNull @Positive Long> selectedProfileIds = new ArrayList<>();
}
