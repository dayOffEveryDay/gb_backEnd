package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InquiryAuthorResponse {
    private Long id;
    private String displayName;
    private String profileImageUrl;
}
