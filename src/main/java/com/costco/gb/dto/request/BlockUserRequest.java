package com.costco.gb.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BlockUserRequest {
    @NotNull private Long blockedUserId;
}
