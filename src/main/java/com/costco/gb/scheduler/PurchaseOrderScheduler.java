package com.costco.gb.scheduler;

import com.costco.gb.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderScheduler {

    private final PurchaseOrderService purchaseOrderService;

    // 每分鐘處理已超過 12 小時且仍未收到回應的託購異常。
    @Scheduled(cron = "0 * * * * *")
    public void checkPurchaseOrderAbnormalTimeout() {
        int processed = purchaseOrderService.processAbnormalTimeouts();
        if (processed > 0) {
            log.info("已處理 {} 筆託購異常逾時案件", processed);
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void checkPurchaseOrderConfirmationTimeout() {
        purchaseOrderService.processConfirmationTimeouts();
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void autoCompletePurchaseOrders() {
        purchaseOrderService.autoCompleteOrders();
    }
}
