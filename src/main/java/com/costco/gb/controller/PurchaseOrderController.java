package com.costco.gb.controller;

import com.costco.gb.dto.request.CancelPurchaseOrderRequest;
import com.costco.gb.dto.request.ItemUnavailableRequest;
import com.costco.gb.dto.request.ReportPurchaseOrderAbnormalRequest;
import com.costco.gb.dto.request.RespondPurchaseOrderAbnormalRequest;
import com.costco.gb.dto.request.PurchaseChatSendRequest;
import com.costco.gb.dto.request.ShipPurchaseOrderRequest;
import com.costco.gb.dto.response.PurchaseOrderDetailResponse;
import com.costco.gb.dto.response.PurchaseOrderEventResponse;
import com.costco.gb.dto.response.PurchaseOrderSummaryResponse;
import com.costco.gb.dto.response.PurchaseChatMessageResponse;
import com.costco.gb.service.PurchaseChatService;
import com.costco.gb.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseChatService purchaseChatService;

    @GetMapping
    public ResponseEntity<Page<PurchaseOrderSummaryResponse>> getMyOrders(
            Authentication authentication,
            @RequestParam String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(purchaseOrderService.listMyOrders(
                currentUserId(authentication), role, status, page, size
        ));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PurchaseOrderDetailResponse> getDetail(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(purchaseOrderService.getDetail(currentUserId(authentication), orderId));
    }

    @PostMapping("/{orderId}/confirm/requester")
    public ResponseEntity<PurchaseOrderDetailResponse> confirmRequester(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(purchaseOrderService.confirmRequester(currentUserId(authentication), orderId));
    }

    @PostMapping("/{orderId}/confirm/runner")
    public ResponseEntity<PurchaseOrderDetailResponse> confirmRunner(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(purchaseOrderService.confirmRunner(currentUserId(authentication), orderId));
    }

    @PostMapping("/{orderId}/start")
    public ResponseEntity<PurchaseOrderDetailResponse> start(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(purchaseOrderService.start(currentUserId(authentication), orderId));
    }

    @PostMapping("/{orderId}/item-unavailable")
    public ResponseEntity<PurchaseOrderDetailResponse> reportUnavailable(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody ItemUnavailableRequest request
    ) {
        return ResponseEntity.ok(purchaseOrderService.reportUnavailable(
                currentUserId(authentication), orderId, request
        ));
    }

    @PostMapping("/{orderId}/cancel/requester")
    public ResponseEntity<PurchaseOrderDetailResponse> cancelByRequester(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody CancelPurchaseOrderRequest request
    ) {
        return ResponseEntity.ok(purchaseOrderService.cancelByRequester(
                currentUserId(authentication), orderId, request
        ));
    }

    @PostMapping("/{orderId}/cancel/runner")
    public ResponseEntity<PurchaseOrderDetailResponse> cancelByRunner(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody CancelPurchaseOrderRequest request
    ) {
        return ResponseEntity.ok(purchaseOrderService.cancelByRunner(
                currentUserId(authentication), orderId, request
        ));
    }

    @PostMapping("/{orderId}/ship")
    public ResponseEntity<PurchaseOrderDetailResponse> ship(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody ShipPurchaseOrderRequest request
    ) {
        return ResponseEntity.ok(purchaseOrderService.ship(currentUserId(authentication), orderId, request));
    }

    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<PurchaseOrderDetailResponse> deliver(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(purchaseOrderService.deliver(currentUserId(authentication), orderId));
    }

    @PostMapping("/{orderId}/complete")
    public ResponseEntity<PurchaseOrderDetailResponse> complete(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(purchaseOrderService.complete(currentUserId(authentication), orderId));
    }

    @PostMapping("/{orderId}/abnormal/report")
    public ResponseEntity<PurchaseOrderDetailResponse> reportAbnormal(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody ReportPurchaseOrderAbnormalRequest request
    ) {
        return ResponseEntity.ok(purchaseOrderService.reportAbnormal(
                currentUserId(authentication), orderId, request
        ));
    }

    @PostMapping("/{orderId}/abnormal/respond")
    public ResponseEntity<PurchaseOrderDetailResponse> respondAbnormal(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody RespondPurchaseOrderAbnormalRequest request
    ) {
        return ResponseEntity.ok(purchaseOrderService.respondAbnormal(
                currentUserId(authentication), orderId, request
        ));
    }

    @GetMapping("/{orderId}/events")
    public ResponseEntity<List<PurchaseOrderEventResponse>> getEvents(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(purchaseOrderService.getEvents(currentUserId(authentication), orderId));
    }

    @GetMapping("/{orderId}/messages")
    public ResponseEntity<List<PurchaseChatMessageResponse>> getMessages(
            Authentication authentication, @PathVariable Long orderId) {
        return ResponseEntity.ok(purchaseChatService.getMessagesByOrder(currentUserId(authentication), orderId));
    }

    @PostMapping("/{orderId}/messages")
    public ResponseEntity<PurchaseChatMessageResponse> sendMessage(
            Authentication authentication, @PathVariable Long orderId,
            @Valid @RequestBody PurchaseChatSendRequest request) {
        return ResponseEntity.ok(purchaseChatService.sendByOrder(
                currentUserId(authentication), orderId, request.getMessageType(), request.getContent()));
    }

    @PostMapping("/{orderId}/messages/images")
    public ResponseEntity<PurchaseChatMessageResponse> sendImage(
            Authentication authentication, @PathVariable Long orderId,
            @Valid @RequestBody PurchaseChatSendRequest request) {
        return ResponseEntity.ok(purchaseChatService.sendByOrder(
                currentUserId(authentication), orderId, "IMAGE", request.getContent()));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getPrincipal().toString());
    }
}
