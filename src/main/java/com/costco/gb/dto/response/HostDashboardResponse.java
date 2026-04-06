package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class HostDashboardResponse {

    // 📊 第一部分：合購單庫存總覽
    private Long campaignId;
    private String itemName;             // 商品名稱
    private String status;               // 狀態 (OPEN, FULL 等)
    private Integer totalPhysicalQuantity; // 實體商品總數 (開放 + 自留)
    private Integer openQuantity;        // 開放給團員認購的數量
    private Integer hostReservedQuantity;// 團主自留數量
    private Integer alreadySoldQuantity; // 已經被團員認購的數量
    private Integer availableQuantity;   // 剩下還沒賣掉的數量

    // 👥 第二部分：乘客名單 (誰買了多少)
    private List<ParticipantDetail> participants;

    @Data
    @Builder
    public static class ParticipantDetail {
        private Long participantsId;
        private Long userId;
        private String displayName; // 團員暱稱
        private Integer quantity;// 認購數量
        private String status;   // 狀態 (JOINED 或是 CANCELLED 跳車)
    }
}