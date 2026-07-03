package com.costco.gb.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class CreatePurchaseRequestRequest {

    @NotBlank(message = "商品名稱不可空白")
    private String itemName;

    private String itemDescription;
    private List<String> imageUrls = new ArrayList<>();

    @NotBlank(message = "酬金模式不可空白")
    private String rewardType;

    private Integer fixedRewardAmount;

    @NotBlank(message = "委託人城市不可空白")
    private String requesterCity;

    private String requesterDistrict;

    @NotBlank(message = "交付方式不可空白")
    private String deliveryMethod;

    @NotBlank(message = "公開交付摘要不可空白")
    private String deliveryPublicSummary;

    private Map<String, Object> deliveryPrivateDetail;

    @NotNull(message = "委託期限不可為空")
    private LocalDateTime requestExpireTime;

    @NotNull(message = "交貨期限時數不可為空")
    private Integer deliveryDeadlineHours;

    private Integer minCreditScore = 0;
    private List<Long> selectedProfileIds = new ArrayList<>();

    @Valid
    private List<MeetupRangeRequest> manualMeetupRanges = new ArrayList<>();

    private Boolean saveManualToProfile = false;
    private Boolean saveDeliveryToProfile = false;
}
