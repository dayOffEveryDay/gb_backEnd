package com.costco.gb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * @deprecated 不建議再使用，請改用 CreditScoreLogResponse
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditLogResponse {
    private Long id;
    private Integer scoreChange;
    private String reason;
    private Long campaignId;
    private LocalDateTime createdAt;
}