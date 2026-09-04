package com.costco.gb.scheduler;

import com.costco.gb.service.InquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InquiryScheduler {

    private final InquiryService inquiryService;

    // 每分鐘將到期的庫存問題關閉，並處理未獲回答時的通知。
    @Scheduled(cron = "0 * * * * *")
    public void expireStockInquiries() {
        inquiryService.expireStockInquiries();
    }
}
