package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateInquiryAnswerRequest {

    private Integer storeId;

    private String stockStatus;

    @Size(max = 65535, message = "回答內容過長")
    private String content;
}
