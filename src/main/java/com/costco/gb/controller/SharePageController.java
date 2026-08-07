package com.costco.gb.controller;

import com.costco.gb.service.SharePageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/share")
@RequiredArgsConstructor
public class SharePageController {

    private static final MediaType HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

    private final SharePageService sharePageService;

    @GetMapping(value = "/campaigns/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> campaign(@PathVariable Long id) {
        return htmlResponse(sharePageService.renderCampaignPage(id));
    }

    @GetMapping(value = "/purchase-requests/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> purchaseRequest(@PathVariable Long id) {
        return htmlResponse(sharePageService.renderPurchaseRequestPage(id));
    }

    private ResponseEntity<String> htmlResponse(String html) {
        // 短時間快取可降低聊天軟體重複抓取壓力，也不會讓商品狀態長時間停留在舊資料。
        return ResponseEntity.ok()
                .contentType(HTML_UTF8)
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(html);
    }
}
