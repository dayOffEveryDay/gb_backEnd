package com.costco.gb.controller;

import com.costco.gb.dto.request.CreateCampaignRequest;
import com.costco.gb.dto.response.CampaignSummaryResponse;
import com.costco.gb.dto.response.CreditLogResponse;
import com.costco.gb.service.CampaignService;
import com.costco.gb.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.costco.gb.dto.request.JoinCampaignRequest;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;
    private final UserService userService;
    @GetMapping
    public ResponseEntity<Page<CampaignSummaryResponse>> getCampaigns(
            @RequestParam(required = false) Integer storeId,
            @RequestParam(required = false) Integer categoryId, // 新增分類
            @RequestParam(required = false) String keyword,     // 新增關鍵字
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<CampaignSummaryResponse> response = campaignService.getActiveCampaigns(storeId, categoryId, keyword, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createCampaign(@ModelAttribute CreateCampaignRequest request) {

        // ✨ 從 Token 攔截器中拿出剛剛解析好的 userId
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long hostId = Long.parseLong(userIdStr);

        // 呼叫 Service 執行開團
        campaignService.createCampaign(hostId, request);

        // 回傳成功訊息
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "合購單發起成功！"
        ));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinCampaign(
            @PathVariable("id") Long campaignId,
            @Valid @RequestBody JoinCampaignRequest request
    ) {
        // 從 Token 取出當前使用者 ID
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(userIdStr);

        // 呼叫 Service
        campaignService.joinCampaign(userId, campaignId, request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "成功加入合購！"
        ));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<?> withdrawCampaign(@PathVariable("id") Long campaignId) {

        // 從 Token 攔截器中拿出當前使用者 ID
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(userIdStr);

        // 呼叫 Service 執行退出
        campaignService.withdrawCampaign(userId, campaignId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "您已成功退出合購，相關紀錄已更新。"
        ));
    }

    // 團主宣告已面交
    @PostMapping("/{id}/deliver")
    public ResponseEntity<?> deliverCampaign(@PathVariable("id") Long campaignId) {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(userIdStr);

        campaignService.deliverCampaign(userId, campaignId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "已成功更改為待確認狀態，請等待團員點擊收貨。"
        ));
    }

    // 團員確認收貨
    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirmReceipt(@PathVariable("id") Long campaignId) {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(userIdStr);

        campaignService.confirmReceipt(userId, campaignId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "您已確認收貨！感謝您的參與。"
        ));
    }
    // 團主主動取消合購單
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelCampaign(@PathVariable("id") Long campaignId) {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(userIdStr);

        campaignService.cancelCampaignByHost(userId, campaignId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "合購單已成功取消。若有團員已加入，您的信用分數將會受到相應的扣除。"
        ));
    }

    // 🌟 查詢個人信用分明細 (信用存摺)
    @GetMapping("/me/credit-logs")
    public ResponseEntity<Page<CreditLogResponse>> getMyCreditLogs(
            @org.springframework.data.web.PageableDefault(page = 0, size = 10) org.springframework.data.domain.Pageable pageable) {

        // ✨ 一樣的做法
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(userIdStr);

        Page<CreditLogResponse> response = userService.getMyCreditLogs(userId, pageable);

        return ResponseEntity.ok(response);
    }
    // 🌟 取得「我開的團」列表
    @GetMapping("/my-hosted")
    public ResponseEntity<Page<CampaignSummaryResponse>> getMyHostedCampaigns(
            @org.springframework.data.web.PageableDefault(page = 0, size = 10) org.springframework.data.domain.Pageable pageable) {

        // ✨ 用你原本成功的寫法，直接從 SecurityContext 拿！
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(userIdStr);

        Page<CampaignSummaryResponse> response = campaignService.getMyHostedCampaigns(userId, pageable);
        return ResponseEntity.ok(response);
    }

    // 🌟 取得「我跟的團」列表
    @GetMapping("/my-joined")
    public ResponseEntity<Page<CampaignSummaryResponse>> getMyJoinedCampaigns(
            @org.springframework.data.web.PageableDefault(page = 0, size = 10) org.springframework.data.domain.Pageable pageable) {

        // ✨ 一樣的做法
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(userIdStr);

        Page<CampaignSummaryResponse> response = campaignService.getMyJoinedCampaigns(userId, pageable);
        return ResponseEntity.ok(response);
    }
}