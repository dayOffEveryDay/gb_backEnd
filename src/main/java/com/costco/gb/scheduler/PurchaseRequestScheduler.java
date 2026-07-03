package com.costco.gb.scheduler;

import com.costco.gb.service.PurchaseRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class PurchaseRequestScheduler {
    private final PurchaseRequestService purchaseRequestService;

    @Scheduled(cron = "0 * * * * *")
    public void expirePurchaseRequests() {
        purchaseRequestService.expirePurchaseRequests();
    }
}
