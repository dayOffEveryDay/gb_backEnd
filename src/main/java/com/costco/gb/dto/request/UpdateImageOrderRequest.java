package com.costco.gb.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class UpdateImageOrderRequest {
    // 前端傳來的全新排序網址陣列
    // 例如: ["/uploads/img3.jpg", "/uploads/img1.jpg", "/uploads/img2.jpg"]
    private List<String> imageUrls;
}