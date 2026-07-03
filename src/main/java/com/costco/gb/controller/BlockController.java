package com.costco.gb.controller;

import com.costco.gb.dto.request.BlockUserRequest;
import com.costco.gb.service.BlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/blocks") @RequiredArgsConstructor
public class BlockController {
    private final BlockService blockService;

    @PostMapping
    public ResponseEntity<Void> block(Authentication auth, @Valid @RequestBody BlockUserRequest request) {
        blockService.blockUser(Long.parseLong(auth.getPrincipal().toString()), request.getBlockedUserId());
        return ResponseEntity.noContent().build();
    }
}
