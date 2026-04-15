package com.costco.gb.controller;

import com.costco.gb.dto.response.CreditScoreLogResponse;
import com.costco.gb.service.CreditScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credit-scores")
@RequiredArgsConstructor
public class CreditScoreController {

    private final CreditScoreService creditScoreService;

    // 獲取我的信用分數異動紀錄
    @GetMapping("/me/logs")
    public ResponseEntity<Page<CreditScoreLogResponse>> getMyCreditScoreLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long currentUserId = Long.parseLong(userIdStr);

        return ResponseEntity.ok(creditScoreService.getMyCreditScoreLogs(currentUserId, page, size));
    }
}