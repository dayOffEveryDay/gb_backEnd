package com.costco.gb.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreatePurchaseRequestRequest {
    private String productName;
    private String rewardType;
    private BigDecimal fixedRewardAmount;
    private String deliveryMethod;
    private String requestArea;
    private LocalDateTime deadlineAt;
    private String deliveryTimeType;
    private String deliveryTimeNote;
    private Integer minCreditScore;
    private String description;
    private List<MultipartFile> images;
}
