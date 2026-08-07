package com.costco.gb.service;

import com.costco.gb.dto.request.CancelPurchaseOrderRequest;
import com.costco.gb.dto.request.ItemUnavailableRequest;
import com.costco.gb.dto.request.ReportPurchaseOrderAbnormalRequest;
import com.costco.gb.dto.request.RespondPurchaseOrderAbnormalRequest;
import com.costco.gb.dto.request.ShipPurchaseOrderRequest;
import com.costco.gb.dto.response.MeetupRangeResponse;
import com.costco.gb.dto.response.PurchaseOrderDetailResponse;
import com.costco.gb.dto.response.PurchaseOrderEventResponse;
import com.costco.gb.dto.response.PurchaseOrderSummaryResponse;
import com.costco.gb.entity.PurchaseOrder;
import com.costco.gb.entity.PurchaseChatRoom;
import com.costco.gb.entity.PurchaseOrderEvent;
import com.costco.gb.entity.PurchaseQuote;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.PurchaseRequestMeetupRange;
import com.costco.gb.entity.User;
import com.costco.gb.repository.PurchaseOrderEventRepository;
import com.costco.gb.repository.PurchaseChatRoomRepository;
import com.costco.gb.repository.PurchaseOrderRepository;
import com.costco.gb.repository.PurchaseQuoteRepository;
import com.costco.gb.repository.PurchaseRequestMeetupRangeRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final Set<String> ORDER_STATUSES = Set.of(
            "WAITING_CONFIRMATION", "CONFIRMED", "IN_PROGRESS", "SHIPPED",
            "DELIVERED_PENDING_CONFIRM", "COMPLETED", "CANCELLED",
            "UNAVAILABLE_CANCELLED", "ABNORMAL_PENDING_RESPONSE",
            "PENALIZED_CANCELLED", "DISPUTE_CLOSED_NO_PENALTY"
    );
    private static final Set<String> CANCELLABLE_STATUSES = Set.of(
            "WAITING_CONFIRMATION", "CONFIRMED", "IN_PROGRESS"
    );
    private static final Set<String> ABNORMAL_TYPES = Set.of(
            "REQUESTER_NO_SHOW", "RUNNER_NO_SHOW", "RUNNER_NOT_DELIVERED",
            "REQUESTER_UNREACHABLE", "ITEM_NOT_RECEIVED", "OTHER"
    );

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderEventRepository orderEventRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestMeetupRangeRepository meetupRangeRepository;
    private final PurchaseQuoteRepository quoteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PurchaseChatRoomRepository chatRoomRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional(readOnly = true)
    public Page<PurchaseOrderSummaryResponse> listMyOrders(
            Long userId,
            String role,
            String status,
            int page,
            int size
    ) {
        String normalizedRole = normalizeRequired(role, "訂單角色不可空白");
        if (!Set.of("REQUESTER", "RUNNER").contains(normalizedRole)) {
            throw new RuntimeException("訂單角色只允許 REQUESTER 或 RUNNER");
        }
        String normalizedStatus = normalizeOptional(status);
        if (normalizedStatus != null && !ORDER_STATUSES.contains(normalizedStatus)) {
            throw new RuntimeException("不支援的訂單狀態: " + normalizedStatus);
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new RuntimeException("分頁參數不合法，size 必須介於 1 到 100");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PurchaseOrder> orders;
        if ("REQUESTER".equals(normalizedRole)) {
            orders = normalizedStatus == null
                    ? orderRepository.findByRequesterIdOrderByCreatedAtDesc(userId, pageable)
                    : orderRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(
                            userId, normalizedStatus, pageable
                    );
        } else {
            orders = normalizedStatus == null
                    ? orderRepository.findByRunnerIdOrderByCreatedAtDesc(userId, pageable)
                    : orderRepository.findByRunnerIdAndStatusOrderByCreatedAtDesc(
                            userId, normalizedStatus, pageable
                    );
        }
        return orders.map(order -> toSummaryResponse(order, normalizedRole));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDetailResponse getDetail(Long userId, Long orderId) {
        PurchaseOrder order = findOrder(orderId);
        validateParty(order, userId);
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse confirmRequester(Long userId, Long orderId) {
        PurchaseOrder order = lockOrder(orderId);
        if (!order.getRequester().getId().equals(userId)) {
            throw new RuntimeException("只有委託人可以執行此確認");
        }
        if (order.getRequesterConfirmedAt() != null) {
            return toDetailResponse(order, userId);
        }
        requireStatus(order, "WAITING_CONFIRMATION");

        order.setRequesterConfirmedAt(LocalDateTime.now());
        recordEvent(order, order.getRequester(), "REQUESTER_CONFIRMED", order.getStatus(), order.getStatus(), null);
        completeConfirmationIfReady(order);
        orderRepository.save(order);
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse confirmRunner(Long userId, Long orderId) {
        PurchaseOrder order = lockOrder(orderId);
        if (!order.getRunner().getId().equals(userId)) {
            throw new RuntimeException("只有接單人可以執行此確認");
        }
        if (order.getRunnerConfirmedAt() != null) {
            return toDetailResponse(order, userId);
        }
        requireStatus(order, "WAITING_CONFIRMATION");

        order.setRunnerConfirmedAt(LocalDateTime.now());
        recordEvent(order, order.getRunner(), "RUNNER_CONFIRMED", order.getStatus(), order.getStatus(), null);
        completeConfirmationIfReady(order);
        orderRepository.save(order);
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse start(Long userId, Long orderId) {
        PurchaseOrder order = lockOrder(orderId);
        validateRunner(order, userId);
        requireStatus(order, "CONFIRMED");

        String fromStatus = order.getStatus();
        order.setStatus("IN_PROGRESS");
        order.setStartedAt(LocalDateTime.now());
        orderRepository.save(order);
        recordEvent(order, order.getRunner(), "RUNNER_STARTED", fromStatus, order.getStatus(), null);
        notify(order.getRequester(), "PURCHASE_ORDER_STARTED", order, "接單人已開始處理託購");
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse reportUnavailable(
            Long userId,
            Long orderId,
            ItemUnavailableRequest request
    ) {
        validateUnavailablePayload(request);
        PurchaseOrder order = lockOrder(orderId);
        validateRunner(order, userId);
        if (!Set.of("WAITING_CONFIRMATION", "CONFIRMED", "IN_PROGRESS").contains(order.getStatus())) {
            throw new RuntimeException("目前訂單狀態不可回報缺貨");
        }

        String fromStatus = order.getStatus();
        order.setStatus("UNAVAILABLE_CANCELLED");
        order.setCancelReason("ITEM_UNAVAILABLE");
        order.setBlameUser(null);
        order.setCreditPenalty(0);
        order.setCancelledAt(LocalDateTime.now());
        reopenOrExpireRequest(order.getPurchaseRequest(), "ITEM_UNAVAILABLE", true);

        PurchaseQuote selectedQuote = order.getSelectedQuote();
        if (selectedQuote != null) {
            selectedQuote.setStatus("UNAVAILABLE_CANCELLED");
            quoteRepository.save(selectedQuote);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reason", request.getReason());
        metadata.put("imageUrls", request.getImageUrls() == null ? List.of() : request.getImageUrls());
        metadata.put("storeName", request.getStoreName());
        orderRepository.save(order);
        recordEvent(order, order.getRunner(), "ITEM_UNAVAILABLE_REPORTED", fromStatus, order.getStatus(), metadata);
        notify(order.getRequester(), "PURCHASE_ITEM_UNAVAILABLE", order, "接單人回報商品缺貨");
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse cancelByRequester(
            Long userId,
            Long orderId,
            CancelPurchaseOrderRequest request
    ) {
        validateCancelPayload(request);
        PurchaseOrder order = lockOrder(orderId);
        if (!order.getRequester().getId().equals(userId)) {
            throw new RuntimeException("只有委託人可以取消此訂單");
        }
        validateCancellable(order);

        String fromStatus = order.getStatus();
        int penalty = "IN_PROGRESS".equals(fromStatus) ? 10 : 2;
        applyPenalty(order.getRequester(), penalty);
        closePenalizedOrder(order, order.getRequester(), "REQUESTER_CANCEL", penalty);

        // 已開始處理的委託不再重新募集；處理前取消仍可在原期限內重新開放。
        if ("IN_PROGRESS".equals(fromStatus)) {
            order.getPurchaseRequest().setStatus("CANCELLED");
            order.getPurchaseRequest().setCancelledAt(LocalDateTime.now());
            purchaseRequestRepository.save(order.getPurchaseRequest());
        } else {
            reopenOrExpireRequest(order.getPurchaseRequest(), "REQUESTER_CANCEL", true);
        }

        Map<String, Object> metadata = Map.of("reason", request.getReason(), "creditPenalty", penalty);
        orderRepository.save(order);
        recordEvent(order, order.getRequester(), "ORDER_CANCELLED_BY_REQUESTER", fromStatus, order.getStatus(), metadata);
        notify(order.getRunner(), "PURCHASE_ORDER_CANCELLED", order, "委託人已取消訂單");
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse cancelByRunner(
            Long userId,
            Long orderId,
            CancelPurchaseOrderRequest request
    ) {
        validateCancelPayload(request);
        PurchaseOrder order = lockOrder(orderId);
        validateRunner(order, userId);
        validateCancellable(order);

        String fromStatus = order.getStatus();
        int penalty = "WAITING_CONFIRMATION".equals(fromStatus) ? 2 : 10;
        applyPenalty(order.getRunner(), penalty);
        closePenalizedOrder(order, order.getRunner(), "RUNNER_CANCEL", penalty);
        reopenOrExpireRequest(order.getPurchaseRequest(), "RUNNER_CANCEL", true);

        Map<String, Object> metadata = Map.of("reason", request.getReason(), "creditPenalty", penalty);
        orderRepository.save(order);
        recordEvent(order, order.getRunner(), "ORDER_CANCELLED_BY_RUNNER", fromStatus, order.getStatus(), metadata);
        notify(order.getRequester(), "PURCHASE_ORDER_CANCELLED", order, "接單人已取消訂單");
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse ship(
            Long userId,
            Long orderId,
            ShipPurchaseOrderRequest request
    ) {
        validateShipmentPayload(request);
        PurchaseOrder order = lockOrder(orderId);
        validateRunner(order, userId);
        requireStatus(order, "IN_PROGRESS");
        if (!Set.of("HOME_DELIVERY", "STORE_TO_STORE")
                .contains(order.getPurchaseRequest().getDeliveryMethod())) {
            throw new RuntimeException("只有宅配或店到店訂單可以標記寄出");
        }

        String fromStatus = order.getStatus();
        order.setStatus("SHIPPED");
        order.setLogisticsProvider(request.getLogisticsProvider().trim());
        order.setTrackingNumber(request.getTrackingNumber().trim());
        order.setTrackingUrl(request.getTrackingUrl());
        order.setShippedAt(LocalDateTime.now());
        orderRepository.save(order);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("logisticsProvider", order.getLogisticsProvider());
        metadata.put("trackingNumber", order.getTrackingNumber());
        metadata.put("trackingUrl", order.getTrackingUrl());
        recordEvent(order, order.getRunner(), "ORDER_SHIPPED", fromStatus, order.getStatus(), metadata);
        notify(order.getRequester(), "PURCHASE_ORDER_SHIPPED", order, "託購商品已寄出");
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse deliver(Long userId, Long orderId) {
        PurchaseOrder order = lockOrder(orderId);
        validateRunner(order, userId);
        requireStatus(order, "IN_PROGRESS");
        if (!"FACE_TO_FACE".equals(order.getPurchaseRequest().getDeliveryMethod())) {
            throw new RuntimeException("只有面交訂單可以標記已交貨");
        }

        String fromStatus = order.getStatus();
        order.setStatus("DELIVERED_PENDING_CONFIRM");
        order.setDeliveredAt(LocalDateTime.now());
        orderRepository.save(order);
        recordEvent(order, order.getRunner(), "RUNNER_MARKED_DELIVERED", fromStatus, order.getStatus(), null);
        notify(order.getRequester(), "PURCHASE_ORDER_DELIVERED", order, "接單人已標記交貨，請確認收貨");
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse complete(Long userId, Long orderId) {
        PurchaseOrder order = lockOrder(orderId);
        if (!order.getRequester().getId().equals(userId)) {
            throw new RuntimeException("只有委託人可以完成此訂單");
        }
        boolean allowed = Set.of("SHIPPED", "DELIVERED_PENDING_CONFIRM").contains(order.getStatus())
                || ("IN_PROGRESS".equals(order.getStatus())
                && "FACE_TO_FACE".equals(order.getPurchaseRequest().getDeliveryMethod()));
        if (!allowed) {
            throw new RuntimeException("目前訂單狀態不可完成");
        }

        String fromStatus = order.getStatus();
        order.setStatus("COMPLETED");
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);
        recordEvent(order, order.getRequester(), "ORDER_COMPLETED_BY_REQUESTER", fromStatus, order.getStatus(), null);
        notify(order.getRunner(), "PURCHASE_ORDER_COMPLETED", order, "委託人已確認完成託購訂單");
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse reportAbnormal(
            Long userId,
            Long orderId,
            ReportPurchaseOrderAbnormalRequest request
    ) {
        validateAbnormalReportPayload(request);
        PurchaseOrder order = lockOrder(orderId);
        validateParty(order, userId);
        if (!Set.of("IN_PROGRESS", "SHIPPED", "DELIVERED_PENDING_CONFIRM").contains(order.getStatus())) {
            throw new RuntimeException("目前訂單狀態不可提出異常");
        }

        User reporter = order.getRequester().getId().equals(userId)
                ? order.getRequester()
                : order.getRunner();
        User reportedUser = reporter.getId().equals(order.getRequester().getId())
                ? order.getRunner()
                : order.getRequester();
        String fromStatus = order.getStatus();
        LocalDateTime now = LocalDateTime.now();
        order.setStatus("ABNORMAL_PENDING_RESPONSE");
        order.setAbnormalReporter(reporter);
        order.setAbnormalReportedUser(reportedUser);
        order.setAbnormalType(normalizeRequired(request.getAbnormalType(), "異常類型不可空白"));
        order.setAbnormalReportedAt(now);
        order.setAbnormalRespondedAt(null);
        orderRepository.save(order);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("abnormalType", order.getAbnormalType());
        metadata.put("imageUrls", request.getImageUrls() == null ? List.of() : request.getImageUrls());
        recordDetailedEvent(
                order,
                reporter,
                "ABNORMAL_REPORTED",
                fromStatus,
                order.getStatus(),
                request.getDescription(),
                metadata
        );
        notify(
                reportedUser,
                "PURCHASE_ABNORMAL_REPORTED",
                order,
                "交易對方提出異常，請於 12 小時內回應"
        );
        return toDetailResponse(order, userId);
    }

    @Transactional
    public PurchaseOrderDetailResponse respondAbnormal(
            Long userId,
            Long orderId,
            RespondPurchaseOrderAbnormalRequest request
    ) {
        validateAbnormalResponsePayload(request);
        PurchaseOrder order = lockOrder(orderId);
        requireStatus(order, "ABNORMAL_PENDING_RESPONSE");
        if (order.getAbnormalReportedUser() == null
                || !order.getAbnormalReportedUser().getId().equals(userId)) {
            throw new RuntimeException("只有被提出異常的一方可以回應");
        }
        LocalDateTime responseDeadline = order.getAbnormalReportedAt().plusHours(12);
        if (!LocalDateTime.now().isBefore(responseDeadline)) {
            throw new RuntimeException("異常回應期限已過");
        }

        String fromStatus = order.getStatus();
        order.setStatus("DISPUTE_CLOSED_NO_PENALTY");
        order.setAbnormalRespondedAt(LocalDateTime.now());
        orderRepository.save(order);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("imageUrls", request.getImageUrls() == null ? List.of() : request.getImageUrls());
        recordDetailedEvent(
                order,
                order.getAbnormalReportedUser(),
                "ABNORMAL_RESPONDED_NO_PENALTY",
                fromStatus,
                order.getStatus(),
                request.getResponseText(),
                metadata
        );
        notify(
                order.getRequester(),
                "PURCHASE_ABNORMAL_CLOSED",
                order,
                "異常已有回應，本階段不扣分結案"
        );
        notify(
                order.getRunner(),
                "PURCHASE_ABNORMAL_CLOSED",
                order,
                "異常已有回應，本階段不扣分結案"
        );
        return toDetailResponse(order, userId);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderEventResponse> getEvents(Long userId, Long orderId) {
        PurchaseOrder order = findOrder(orderId);
        validateParty(order, userId);
        return orderEventRepository.findByPurchaseOrderIdOrderByCreatedAtAsc(orderId)
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional
    public int processAbnormalTimeouts() {
        LocalDateTime timeoutBefore = LocalDateTime.now().minusHours(12);
        List<PurchaseOrder> candidates = orderRepository
                .findByStatusAndAbnormalReportedAtBeforeAndAbnormalRespondedAtIsNull(
                        "ABNORMAL_PENDING_RESPONSE", timeoutBefore
                );
        int processed = 0;
        for (PurchaseOrder candidate : candidates) {
            PurchaseOrder order = lockOrder(candidate.getId());

            // 取得排他鎖後重新驗證，避免排程與最後一刻提交的回應互相覆蓋。
            if (!"ABNORMAL_PENDING_RESPONSE".equals(order.getStatus())
                    || order.getAbnormalRespondedAt() != null
                    || order.getAbnormalReportedAt() == null
                    || order.getAbnormalReportedAt().isAfter(timeoutBefore)) {
                continue;
            }
            User blamedUser = order.getAbnormalReportedUser();
            if (blamedUser == null) {
                continue;
            }

            String fromStatus = order.getStatus();
            applyPenalty(blamedUser, 10);
            order.setStatus("PENALIZED_CANCELLED");
            order.setBlameUser(blamedUser);
            order.setCreditPenalty(10);
            order.setCancelReason("ABNORMAL_NO_RESPONSE");
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);
            recordDetailedEvent(
                    order,
                    null,
                    "ABNORMAL_TIMEOUT_PENALIZED",
                    fromStatus,
                    order.getStatus(),
                    "被提出異常者於 12 小時內未回應，系統自動扣除信用分",
                    Map.of("creditPenalty", 10, "blameUserId", blamedUser.getId())
            );
            notify(order.getRequester(), "PURCHASE_ABNORMAL_TIMEOUT", order, "異常逾時未回應，系統已扣分結案");
            notify(order.getRunner(), "PURCHASE_ABNORMAL_TIMEOUT", order, "異常逾時未回應，系統已扣分結案");
            processed++;
        }
        return processed;
    }

    @Transactional
    public int processConfirmationTimeouts() {
        LocalDateTime before = LocalDateTime.now().minusHours(24);
        List<PurchaseOrder> candidates = orderRepository.findByStatusAndCreatedAtBefore("WAITING_CONFIRMATION", before);
        int processed = 0;
        for (PurchaseOrder candidate : candidates) {
            PurchaseOrder order = lockOrder(candidate.getId());
            if (!"WAITING_CONFIRMATION".equals(order.getStatus()) || order.getCreatedAt().isAfter(before)) continue;
            String from = order.getStatus();
            User blame = null;
            int penalty = 0;
            if (order.getRequesterConfirmedAt() != null && order.getRunnerConfirmedAt() == null) {
                blame = order.getRunner(); penalty = 10;
            } else if (order.getRunnerConfirmedAt() != null && order.getRequesterConfirmedAt() == null) {
                blame = order.getRequester(); penalty = 2;
            }
            if (penalty > 0) {
                applyPenalty(blame, penalty);
                closePenalizedOrder(order, blame, "CONFIRMATION_TIMEOUT", penalty);
            } else {
                order.setStatus("CANCELLED");
                order.setCancelReason("CONFIRMATION_TIMEOUT");
                order.setCancelledAt(LocalDateTime.now());
            }
            reopenOrExpireRequest(order.getPurchaseRequest(), "CONFIRMATION_TIMEOUT", true);
            orderRepository.save(order);
            recordDetailedEvent(order, null, "ORDER_CONFIRMATION_TIMEOUT", from, order.getStatus(),
                    "訂單成立超過 24 小時仍未完成雙方確認", Map.of("creditPenalty", penalty));
            notify(order.getRequester(), "PURCHASE_CONFIRMATION_TIMEOUT", order, "訂單確認逾時已結案");
            notify(order.getRunner(), "PURCHASE_CONFIRMATION_TIMEOUT", order, "訂單確認逾時已結案");
            processed++;
        }
        return processed;
    }

    @Transactional
    public int autoCompleteOrders() {
        List<PurchaseOrder> candidates = orderRepository.findByStatusIn(
                List.of("IN_PROGRESS", "SHIPPED", "DELIVERED_PENDING_CONFIRM"));
        int processed = 0;
        LocalDateTime now = LocalDateTime.now();
        for (PurchaseOrder candidate : candidates) {
            LocalDateTime deadline = resolveDeliveryDeadline(candidate);
            if (deadline == null || now.isBefore(deadline.plusHours(24))) continue;
            PurchaseOrder order = lockOrder(candidate.getId());
            if (!Set.of("IN_PROGRESS", "SHIPPED", "DELIVERED_PENDING_CONFIRM").contains(order.getStatus())) continue;
            String from = order.getStatus();
            order.setStatus("COMPLETED");
            order.setCompletedAt(now);
            orderRepository.save(order);
            recordEvent(order, null, "ORDER_AUTO_COMPLETED", from, order.getStatus(), null);
            notify(order.getRequester(), "PURCHASE_ORDER_AUTO_COMPLETED", order, "託購訂單已由系統自動完成");
            notify(order.getRunner(), "PURCHASE_ORDER_AUTO_COMPLETED", order, "託購訂單已由系統自動完成");
            processed++;
        }
        return processed;
    }

    private void completeConfirmationIfReady(PurchaseOrder order) {
        if (order.getRequesterConfirmedAt() != null && order.getRunnerConfirmedAt() != null) {
            String fromStatus = order.getStatus();
            order.setStatus("CONFIRMED");
            recordEvent(order, null, "ORDER_CONFIRMED", fromStatus, order.getStatus(), null);
        }
    }

    private void closePenalizedOrder(PurchaseOrder order, User blameUser, String reason, int penalty) {
        order.setStatus("PENALIZED_CANCELLED");
        order.setCancelReason(reason);
        order.setBlameUser(blameUser);
        order.setCreditPenalty(penalty);
        order.setCancelledAt(LocalDateTime.now());
    }

    private void applyPenalty(User user, int penalty) {
        int currentScore = user.getCreditScore() == null ? 100 : user.getCreditScore();
        user.setCreditScore(Math.max(0, currentScore - penalty));
        userRepository.save(user);
    }

    private void reopenOrExpireRequest(PurchaseRequest purchaseRequest, String reason, boolean reopen) {
        if (reopen && purchaseRequest.getRequestExpireTime().isAfter(LocalDateTime.now())) {
            purchaseRequest.setStatus("OPEN");
            purchaseRequest.setReopenCount((purchaseRequest.getReopenCount() == null ? 0 : purchaseRequest.getReopenCount()) + 1);
            purchaseRequest.setLastReopenReason(reason);
        } else {
            purchaseRequest.setStatus("EXPIRED");
            purchaseRequest.setExpiredAt(LocalDateTime.now());
        }
        purchaseRequestRepository.save(purchaseRequest);
    }

    private void recordEvent(
            PurchaseOrder order,
            User actor,
            String eventType,
            String fromStatus,
            String toStatus,
            Map<String, Object> metadata
    ) {
        PurchaseOrderEvent event = PurchaseOrderEvent.builder()
                .purchaseOrder(order)
                .actor(actor)
                .eventType(eventType)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .metadata(metadata)
                .build();
        orderEventRepository.save(event);
    }

    private void recordDetailedEvent(
            PurchaseOrder order,
            User actor,
            String eventType,
            String fromStatus,
            String toStatus,
            String description,
            Map<String, Object> metadata
    ) {
        PurchaseOrderEvent event = PurchaseOrderEvent.builder()
                .purchaseOrder(order)
                .actor(actor)
                .eventType(eventType)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .description(description)
                .metadata(metadata)
                .build();
        orderEventRepository.save(event);
    }

    private void notify(User user, String type, PurchaseOrder order, String content) {
        notificationService.sendNotification(user, type, order.getId(), content);
    }

    private void validateParty(PurchaseOrder order, Long userId) {
        if (!order.getRequester().getId().equals(userId) && !order.getRunner().getId().equals(userId)) {
            throw new RuntimeException("只有訂單交易雙方可以查看或操作此訂單");
        }
    }

    private void validateRunner(PurchaseOrder order, Long userId) {
        if (!order.getRunner().getId().equals(userId)) {
            throw new RuntimeException("只有接單人可以執行此操作");
        }
    }

    private void validateCancellable(PurchaseOrder order) {
        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new RuntimeException("目前訂單狀態不可取消");
        }
    }

    private void requireStatus(PurchaseOrder order, String expectedStatus) {
        if (!expectedStatus.equals(order.getStatus())) {
            throw new RuntimeException("目前訂單狀態必須是 " + expectedStatus);
        }
    }

    private void validateCancelPayload(CancelPurchaseOrderRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new RuntimeException("取消原因不可空白");
        }
        if (request.getReason().length() > 100) {
            throw new RuntimeException("取消原因不可超過 100 字");
        }
    }

    private void validateUnavailablePayload(ItemUnavailableRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new RuntimeException("缺貨原因不可空白");
        }
        if (request.getReason().length() > 500) {
            throw new RuntimeException("缺貨說明不可超過 500 字");
        }
    }

    private void validateShipmentPayload(ShipPurchaseOrderRequest request) {
        if (request == null || request.getLogisticsProvider() == null
                || request.getLogisticsProvider().isBlank()) {
            throw new RuntimeException("物流公司不可空白");
        }
        if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank()) {
            throw new RuntimeException("物流單號不可空白");
        }
    }

    private void validateAbnormalReportPayload(ReportPurchaseOrderAbnormalRequest request) {
        if (request == null) {
            throw new RuntimeException("異常資料不可為空");
        }
        String abnormalType = normalizeRequired(request.getAbnormalType(), "異常類型不可空白");
        if (!ABNORMAL_TYPES.contains(abnormalType)) {
            throw new RuntimeException("不支援的異常類型: " + abnormalType);
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new RuntimeException("異常說明不可空白");
        }
        if (request.getDescription().length() > 1000) {
            throw new RuntimeException("異常說明不可超過 1000 字");
        }
    }

    private void validateAbnormalResponsePayload(RespondPurchaseOrderAbnormalRequest request) {
        if (request == null || request.getResponseText() == null || request.getResponseText().isBlank()) {
            throw new RuntimeException("異常回應不可空白");
        }
        if (request.getResponseText().length() > 1000) {
            throw new RuntimeException("異常回應不可超過 1000 字");
        }
    }

    private PurchaseOrder findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("找不到託購訂單"));
    }

    private PurchaseOrder lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new RuntimeException("找不到託購訂單"));
    }

    private PurchaseOrderSummaryResponse toSummaryResponse(PurchaseOrder order, String role) {
        PurchaseRequest request = order.getPurchaseRequest();
        return PurchaseOrderSummaryResponse.builder()
                .id(order.getId())
                .purchaseRequestId(request.getId())
                .chatRoomId(chatRoomRepository.findByPurchaseOrderId(order.getId())
                        .map(PurchaseChatRoom::getId).orElse(null))
                .itemName(request.getItemName())
                .imageUrls(toPublicImageUrls(request.getImageUrls()))
                .role(role)
                .requesterId(order.getRequester().getId())
                .requesterDisplayName(order.getRequester().getDisplayName())
                .requesterProfileImageUrl(order.getRequester().getProfileImageUrl())
                .runnerId(order.getRunner().getId())
                .runnerDisplayName(order.getRunner().getDisplayName())
                .runnerProfileImageUrl(order.getRunner().getProfileImageUrl())
                .deliveryMethod(request.getDeliveryMethod())
                .rewardType(request.getRewardType())
                .rewardAmount(resolveRewardAmount(order))
                .status(order.getStatus())
                .deliveryDeadlineAt(resolveDeliveryDeadline(order))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private PurchaseOrderDetailResponse toDetailResponse(PurchaseOrder order, Long userId) {
        PurchaseRequest request = order.getPurchaseRequest();
        List<PurchaseRequestMeetupRange> ranges = "FACE_TO_FACE".equals(request.getDeliveryMethod())
                ? meetupRangeRepository.findByPurchaseRequestIdOrderById(request.getId())
                : List.of();
        return PurchaseOrderDetailResponse.builder()
                .id(order.getId())
                .purchaseRequestId(request.getId())
                .chatRoomId(chatRoomRepository.findByPurchaseOrderId(order.getId())
                        .map(PurchaseChatRoom::getId).orElse(null))
                .itemName(request.getItemName())
                .itemDescription(request.getItemDescription())
                .imageUrls(toPublicImageUrls(request.getImageUrls()))
                .requesterId(order.getRequester().getId())
                .requesterDisplayName(order.getRequester().getDisplayName())
                .requesterProfileImageUrl(order.getRequester().getProfileImageUrl())
                .runnerId(order.getRunner().getId())
                .runnerDisplayName(order.getRunner().getDisplayName())
                .runnerProfileImageUrl(order.getRunner().getProfileImageUrl())
                .selectedQuoteId(order.getSelectedQuote() == null ? null : order.getSelectedQuote().getId())
                .rewardType(request.getRewardType())
                .rewardAmount(resolveRewardAmount(order))
                .deliveryMethod(request.getDeliveryMethod())
                .deliveryPublicSummary(request.getDeliveryPublicSummary())
                .deliveryPrivateDetail(request.getDeliveryPrivateDetail())
                .meetupRanges(ranges.stream().map(this::toMeetupRangeResponse).toList())
                .status(order.getStatus())
                .requesterConfirmedAt(order.getRequesterConfirmedAt())
                .runnerConfirmedAt(order.getRunnerConfirmedAt())
                .startedAt(order.getStartedAt())
                .logisticsProvider(order.getLogisticsProvider())
                .trackingNumber(order.getTrackingNumber())
                .trackingUrl(order.getTrackingUrl())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .cancelReason(order.getCancelReason())
                .blameUserId(order.getBlameUser() == null ? null : order.getBlameUser().getId())
                .creditPenalty(order.getCreditPenalty())
                .abnormalReporterId(order.getAbnormalReporter() == null ? null : order.getAbnormalReporter().getId())
                .abnormalReportedUserId(order.getAbnormalReportedUser() == null
                        ? null : order.getAbnormalReportedUser().getId())
                .abnormalType(order.getAbnormalType())
                .abnormalReportedAt(order.getAbnormalReportedAt())
                .abnormalRespondedAt(order.getAbnormalRespondedAt())
                .deliveryDeadlineAt(resolveDeliveryDeadline(order))
                .availableActions(resolveAvailableActions(order, userId))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private List<String> resolveAvailableActions(PurchaseOrder order, Long userId) {
        List<String> actions = new ArrayList<>();
        boolean requester = order.getRequester().getId().equals(userId);
        boolean runner = order.getRunner().getId().equals(userId);
        switch (order.getStatus()) {
            case "WAITING_CONFIRMATION" -> {
                if (requester && order.getRequesterConfirmedAt() == null) actions.add("CONFIRM");
                if (runner && order.getRunnerConfirmedAt() == null) actions.add("CONFIRM");
                actions.add("CANCEL");
            }
            case "CONFIRMED" -> {
                if (runner) actions.add("START");
                actions.add("CANCEL");
            }
            case "IN_PROGRESS" -> {
                if (runner && "FACE_TO_FACE".equals(order.getPurchaseRequest().getDeliveryMethod())) {
                    actions.add("DELIVER");
                }
                if (runner && !"FACE_TO_FACE".equals(order.getPurchaseRequest().getDeliveryMethod())) {
                    actions.add("SHIP");
                }
                if (requester && "FACE_TO_FACE".equals(order.getPurchaseRequest().getDeliveryMethod())) {
                    actions.add("COMPLETE");
                }
                actions.add("CANCEL");
                actions.add("REPORT_ABNORMAL");
            }
            case "SHIPPED", "DELIVERED_PENDING_CONFIRM" -> {
                if (requester) actions.add("COMPLETE");
                actions.add("REPORT_ABNORMAL");
            }
            case "ABNORMAL_PENDING_RESPONSE" -> {
                if (order.getAbnormalReportedUser() != null
                        && order.getAbnormalReportedUser().getId().equals(userId)
                        && order.getAbnormalReportedAt() != null
                        && LocalDateTime.now().isBefore(order.getAbnormalReportedAt().plusHours(12))) {
                    actions.add("RESPOND_ABNORMAL");
                }
            }
            default -> {
            }
        }
        return actions;
    }

    private Integer resolveRewardAmount(PurchaseOrder order) {
        return order.getSelectedQuote() == null
                ? order.getPurchaseRequest().getFixedRewardAmount()
                : order.getSelectedQuote().getQuoteAmount();
    }

    private LocalDateTime resolveDeliveryDeadline(PurchaseOrder order) {
        if (order.getCreatedAt() == null || order.getPurchaseRequest().getDeliveryDeadlineHours() == null) {
            return null;
        }
        return order.getCreatedAt().plusHours(order.getPurchaseRequest().getDeliveryDeadlineHours());
    }

    private MeetupRangeResponse toMeetupRangeResponse(PurchaseRequestMeetupRange range) {
        return MeetupRangeResponse.builder()
                .id(range.getId())
                .sourceProfileId(range.getSourceProfile() == null ? null : range.getSourceProfile().getId())
                .locationType(range.getLocationType())
                .city(range.getCity())
                .district(range.getDistrict())
                .locationName(range.getLocationName())
                .rangeStart(range.getRangeStart())
                .rangeEnd(range.getRangeEnd())
                .displayText(range.getDisplayText())
                .detail(range.getDetail())
                .latitude(range.getLatitude())
                .longitude(range.getLongitude())
                .build();
    }

    private PurchaseOrderEventResponse toEventResponse(PurchaseOrderEvent event) {
        return PurchaseOrderEventResponse.builder()
                .id(event.getId())
                .orderId(event.getPurchaseOrder().getId())
                .actorId(event.getActor() == null ? null : event.getActor().getId())
                .actorDisplayName(event.getActor() == null ? null : event.getActor().getDisplayName())
                .eventType(event.getEventType())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .description(event.getDescription())
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private List<String> copyList(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private List<String> toPublicImageUrls(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return values.stream()
                .map(value -> toPublicImageUrl(value, normalizedBaseUrl))
                .toList();
    }

    private String toPublicImageUrl(String value, String normalizedBaseUrl) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("general/") || trimmed.startsWith("purchase-requests/")
                || trimmed.startsWith("campaigns/")) {
            return normalizedBaseUrl + "/uploads/" + trimmed;
        }
        if (trimmed.startsWith("/uploads/") || trimmed.startsWith("/images/")) {
            return normalizedBaseUrl + trimmed;
        }
        if (trimmed.startsWith("uploads/") || trimmed.startsWith("images/")) {
            return normalizedBaseUrl + "/" + trimmed;
        }
        return trimmed;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }
}
