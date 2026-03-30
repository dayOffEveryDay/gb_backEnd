package com.costco.gb.dto.request;

import lombok.Data;
import java.time.LocalDateTime;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
@Data
public class CreateCampaignRequest {
    private Integer storeId;
    private Integer categoryId;
    private String scenarioType; // "INSTANT" (即時) 或 "SCHEDULED" (預約)
    private String itemName;
    private Integer pricePerUnit;
    private Integer productTotalQuantity; // 商品原始總數 (例如: 36)
    private Integer openQuantity;         // 願意開放給別人買的數量 (例如: 20)
    private String meetupLocation;
    private LocalDateTime meetupTime;
    private LocalDateTime expireTime;
    // (前端上傳時限制最多 3 個檔案)
    private List<MultipartFile> images;
}