package com.costco.gb.dto.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String displayName;
    private Boolean hasCostcoMembership;
}