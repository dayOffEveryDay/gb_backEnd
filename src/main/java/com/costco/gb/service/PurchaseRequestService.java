package com.costco.gb.service;

import com.costco.gb.logging.BusinessLog;
import com.costco.gb.dto.request.CreatePurchaseRequestRequest;
import com.costco.gb.dto.request.UpdatePurchaseRequestRequest;
import com.costco.gb.dto.response.PurchaseRequestDetailResponse;
import com.costco.gb.dto.response.PurchaseRequestSummaryResponse;
import com.costco.gb.entity.PurchaseOrder;
import com.costco.gb.entity.PurchaseQuote;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.User;
import com.costco.gb.repository.PurchaseOrderRepository;
import com.costco.gb.repository.PurchaseQuoteRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRequestService {

    private static final int REOPEN_MINIMUM_HOURS = 24;
    private static final Set<String> REWARD_TYPES = Set.of("FIXED", "QUOTE");
    private static final Set<String> DELIVERY_METHODS = Set.of(
            "FACE_TO_FACE", "HOME_DELIVERY", "STORE_TO_STORE", "IBOX"
    );
    private static final Set<String> REQUEST_STATUSES = Set.of(
            "OPEN", "QUOTING", "ORDERED", "AWAITING_REOPEN", "EXPIRED", "CANCELLED"
    );

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseDeliveryService purchaseDeliveryService;
    private final PurchaseQuoteRepository quoteRepository;
    private final PurchaseOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public Long create(Long userId, CreatePurchaseRequestRequest request) {
        String rewardType = normalizeRequired(request.getRewardType(), "酬金模式不可空白");
        String deliveryMethod = normalizeRequired(request.getDeliveryMethod(), "交付方式不可空白");
        validateCreateRequest(request, rewardType, deliveryMethod);

        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("找不到目前登入使用者"));

        // 先建立託購單，取得主鍵後才能保存交付快照。
        PurchaseRequest purchaseRequest = PurchaseRequest.builder()
                .requester(requester)
                .itemName(request.getItemName().trim())
                .itemDescription(request.getItemDescription())
                .imageUrls(toStorageImageUrls(request.getImageUrls()))
                .rewardType(rewardType)
                .fixedRewardAmount("FIXED".equals(rewardType) ? request.getFixedRewardAmount() : null)
                .deliveryMethod(deliveryMethod)
                .requestExpireTime(request.getRequestExpireTime())
                .deliveryDeadlineHours(request.getDeliveryDeadlineHours())
                .minCreditScore(request.getMinCreditScore() == null ? 0 : request.getMinCreditScore())
                .status("OPEN")
                .build();
        purchaseRequestRepository.save(purchaseRequest);

        // 無論交付方式為何，都從登入者選取的常用資料建立不可變快照。
        purchaseDeliveryService.replaceSnapshots(purchaseRequest, request.getSelectedProfileIds());

        BusinessLog.info(log, "event=PURCHASE_REQUEST_CREATED actorId={} requestId={} status={} rewardType={} rewardAmount={} deliveryMethod={} expiresAt={}",
                userId, purchaseRequest.getId(), purchaseRequest.getStatus(), rewardType, purchaseRequest.getFixedRewardAmount(), deliveryMethod, purchaseRequest.getRequestExpireTime());
        return purchaseRequest.getId();
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequestSummaryResponse> list(
            String status,
            String city,
            String district,
            String deliveryMethod,
            String rewardType,
            String keyword,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new RuntimeException("分頁參數不合法，size 必須介於 1 到 100");
        }

        String normalizedStatus = normalizeOptional(status);
        String normalizedDelivery = normalizeOptional(deliveryMethod);
        String normalizedReward = normalizeOptional(rewardType);
        validateOptionalEnum(normalizedStatus, REQUEST_STATUSES, "委託單狀態");
        validateOptionalEnum(normalizedDelivery, DELIVERY_METHODS, "交付方式");
        validateOptionalEnum(normalizedReward, REWARD_TYPES, "酬金模式");

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return purchaseRequestRepository.findWithFilters(
                normalizedStatus,
                blankToNull(city),
                blankToNull(district),
                normalizedDelivery,
                normalizedReward,
                blankToNull(keyword),
                LocalDateTime.now(),
                pageable
        ).map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequestSummaryResponse> listMyCreated(
            Long userId,
            String status,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new RuntimeException("分頁參數不合法，size 必須介於 1 到 100");
        }
        String normalizedStatus = normalizeOptional(status);
        validateOptionalEnum(normalizedStatus, REQUEST_STATUSES, "委託單狀態");
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return purchaseRequestRepository.findCreatedByUser(userId, normalizedStatus, pageable)
                .map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public PurchaseRequestDetailResponse getDetail(Long requestId, Long currentUserId) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        // 公開查詢僅回傳安全欄位；委託人與已成立訂單雙方可以讀取完整交付快照。
        boolean includePrivate = currentUserId != null && (purchaseRequest.getRequester().getId().equals(currentUserId)
                || orderRepository.findFirstByPurchaseRequestIdOrderByCreatedAtDesc(requestId)
                .filter(order -> isOrderParty(order, currentUserId)).isPresent());
        return toDetailResponse(purchaseRequest, includePrivate);
    }

    @Transactional
    public PurchaseRequestDetailResponse update(Long userId, Long requestId, UpdatePurchaseRequestRequest request) {
        PurchaseRequest purchaseRequest = findOwnedRequest(userId, requestId);
        if (!Set.of("OPEN", "QUOTING").contains(purchaseRequest.getStatus())) {
            throw new RuntimeException("目前委託單狀態不可修改");
        }

        String beforeStatus = purchaseRequest.getStatus();
        Integer beforeReward = purchaseRequest.getFixedRewardAmount();
        String beforeDelivery = purchaseRequest.getDeliveryMethod();
        LocalDateTime beforeExpiry = purchaseRequest.getRequestExpireTime();
        boolean hasActiveQuote = quoteRepository.existsByPurchaseRequestIdAndStatus(requestId, "ACTIVE");
        if ("QUOTING".equals(purchaseRequest.getStatus()) || hasActiveQuote) {
            if (request.hasNonExpiryChanges() || request.getRequestExpireTime() == null) {
                throw new RuntimeException("已有有效報價後只允許延長委託期限");
            }
            extendDeadline(purchaseRequest, request.getRequestExpireTime());
        } else {
            applyOpenRequestChanges(purchaseRequest, request);
            validatePersistedRequest(purchaseRequest);

            if (request.getDeliveryMethod() != null && request.getSelectedProfileIds() == null) {
                throw new RuntimeException("修改交付方式時必須重新選擇常用交付資料");
            }
            if (request.getSelectedProfileIds() != null) {
                purchaseDeliveryService.replaceSnapshots(purchaseRequest, request.getSelectedProfileIds());
            }
        }

        purchaseRequestRepository.save(purchaseRequest);
        BusinessLog.info(log, "event=PURCHASE_REQUEST_UPDATED actorId={} requestId={} beforeStatus={} afterStatus={} beforeReward={} afterReward={} beforeDelivery={} afterDelivery={} beforeExpiry={} afterExpiry={}",
                userId, requestId, beforeStatus, purchaseRequest.getStatus(), beforeReward, purchaseRequest.getFixedRewardAmount(), beforeDelivery, purchaseRequest.getDeliveryMethod(), beforeExpiry, purchaseRequest.getRequestExpireTime());
        return toDetailResponse(purchaseRequest, true);
    }

    @Transactional
    public void cancel(Long userId, Long requestId) {
        PurchaseRequest purchaseRequest = findOwnedRequest(userId, requestId);
        if (!Set.of("OPEN", "QUOTING").contains(purchaseRequest.getStatus())) {
            throw new RuntimeException("只有開放中或報價中的委託單可以取消");
        }

        // 取消委託時一併使所有有效報價失效，不產生信用扣分。
        String beforeStatus = purchaseRequest.getStatus();
        purchaseRequest.setStatus("CANCELLED");
        purchaseRequest.setCancelledAt(LocalDateTime.now());
        quoteRepository.expireActiveQuotes(requestId);
        purchaseRequestRepository.save(purchaseRequest);
        BusinessLog.info(log, "event=PURCHASE_REQUEST_CANCELLED actorId={} requestId={} beforeStatus={} afterStatus=CANCELLED", userId, requestId, beforeStatus);
    }

    @Transactional
    public PurchaseRequestDetailResponse extend(Long userId, Long requestId, LocalDateTime newExpireTime) {
        PurchaseRequest purchaseRequest = findOwnedRequest(userId, requestId);
        if (!Set.of("OPEN", "QUOTING").contains(purchaseRequest.getStatus())) {
            throw new RuntimeException("目前委託單狀態不可延長期限");
        }
        LocalDateTime beforeExpiry = purchaseRequest.getRequestExpireTime();
        extendDeadline(purchaseRequest, newExpireTime);
        BusinessLog.info(log, "event=PURCHASE_REQUEST_EXTENDED actorId={} requestId={} beforeExpiry={} afterExpiry={}", userId, requestId, beforeExpiry, purchaseRequest.getRequestExpireTime());
        purchaseRequestRepository.save(purchaseRequest);

        return toDetailResponse(purchaseRequest, true);
    }

    @Transactional
    public PurchaseRequestDetailResponse reopen(Long userId, Long requestId) {
        PurchaseRequest purchaseRequest = purchaseRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new RuntimeException("找不到委託單"));
        if (!purchaseRequest.getRequester().getId().equals(userId)) {
            throw new RuntimeException("只有委託人可以重新開放此委託單");
        }
        if (!"FIXED".equals(purchaseRequest.getRewardType())) {
            throw new RuntimeException("目前只有固定酬金委託可以重新開放");
        }
        if (!"AWAITING_REOPEN".equals(purchaseRequest.getStatus())) {
            throw new RuntimeException("目前委託單狀態不可重新開放");
        }

        // 重開後至少保留完整 24 小時募集時間；原期限較晚時則維持原期限。
        LocalDateTime minimumExpireTime = LocalDateTime.now().plusHours(REOPEN_MINIMUM_HOURS);
        if (purchaseRequest.getRequestExpireTime().isBefore(minimumExpireTime)) {
            purchaseRequest.setRequestExpireTime(minimumExpireTime);
        }
        purchaseRequest.setStatus("OPEN");
        purchaseRequest.setReopenCount((purchaseRequest.getReopenCount() == null
                ? 0 : purchaseRequest.getReopenCount()) + 1);
        purchaseRequest.setLastReopenReason("ITEM_UNAVAILABLE");
        purchaseRequest.setExpiredAt(null);
        BusinessLog.info(log, "event=PURCHASE_REQUEST_REOPENED actorId={} requestId={} beforeStatus=AWAITING_REOPEN afterStatus=OPEN reopenCount={} expiresAt={}", userId, requestId, purchaseRequest.getReopenCount(), purchaseRequest.getRequestExpireTime());
        purchaseRequestRepository.save(purchaseRequest);

        return toDetailResponse(purchaseRequest, true);
    }

    @Transactional
    public int expirePurchaseRequests() {
        List<PurchaseRequest> expired = purchaseRequestRepository
                .findByStatusInAndRequestExpireTimeBefore(List.of("OPEN", "QUOTING"), LocalDateTime.now());
        int processed = 0;
        for (PurchaseRequest candidate : expired) {
            PurchaseRequest request = purchaseRequestRepository.findByIdForUpdate(candidate.getId())
                    .orElse(null);
            if (request == null || !Set.of("OPEN", "QUOTING").contains(request.getStatus())
                    || request.getRequestExpireTime().isAfter(LocalDateTime.now())) {
                continue;
            }
            String beforeStatus = request.getStatus();
            request.setStatus("EXPIRED");
            request.setExpiredAt(LocalDateTime.now());
            purchaseRequestRepository.save(request);
            List<PurchaseQuote> activeQuotes = quoteRepository
                    .findByPurchaseRequestIdAndStatus(request.getId(), "ACTIVE");
            activeQuotes.forEach(quote -> quote.setStatus("EXPIRED"));
            quoteRepository.saveAll(activeQuotes);
            notificationService.sendNotification(request.getRequester(), "PURCHASE_REQUEST_EXPIRED",
                    request.getId(), "託購委託已到期");
            activeQuotes.forEach(quote -> notificationService.sendNotification(quote.getQuoter(),
                    "PURCHASE_QUOTE_EXPIRED", request.getId(), "您參與報價的託購已到期"));
            BusinessLog.info(log, "event=PURCHASE_REQUEST_EXPIRED actor=SYSTEM requestId={} beforeStatus={} afterStatus=EXPIRED expiredQuoteCount={}", request.getId(), beforeStatus, activeQuotes.size());
            processed++;
        }
        return processed;
    }

    private void validateCreateRequest(
            CreatePurchaseRequestRequest request,
            String rewardType,
            String deliveryMethod
    ) {
        if (!REWARD_TYPES.contains(rewardType)) {
            throw new RuntimeException("不支援的酬金模式: " + rewardType);
        }
        if (!DELIVERY_METHODS.contains(deliveryMethod)) {
            throw new RuntimeException("不支援的交付方式: " + deliveryMethod);
        }
        if ("FIXED".equals(rewardType)
                && (request.getFixedRewardAmount() == null || request.getFixedRewardAmount() <= 0)) {
            throw new RuntimeException("固定酬金模式必須提供大於 0 的酬金");
        }
        if (request.getRequestExpireTime() == null || !request.getRequestExpireTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("委託期限必須是未來時間");
        }
        if (request.getDeliveryDeadlineHours() == null || request.getDeliveryDeadlineHours() <= 0) {
            throw new RuntimeException("交貨期限時數必須大於 0");
        }
        if (request.getMinCreditScore() != null && request.getMinCreditScore() < 0) {
            throw new RuntimeException("最低信用分不可小於 0");
        }

        if (request.getSelectedProfileIds() == null || request.getSelectedProfileIds().isEmpty()) {
            throw new RuntimeException("請選擇至少一筆常用交付資料");
        }
    }

    private void applyOpenRequestChanges(PurchaseRequest purchaseRequest, UpdatePurchaseRequestRequest request) {
        if (request.getItemName() != null) {
            purchaseRequest.setItemName(request.getItemName().trim());
        }
        if (request.getItemDescription() != null) {
            purchaseRequest.setItemDescription(request.getItemDescription());
        }
        if (request.getImageUrls() != null) {
            purchaseRequest.setImageUrls(toStorageImageUrls(request.getImageUrls()));
        }
        if (request.getRewardType() != null) {
            purchaseRequest.setRewardType(normalizeRequired(request.getRewardType(), "酬金模式不可空白"));
        }
        if (request.getFixedRewardAmount() != null) {
            purchaseRequest.setFixedRewardAmount(request.getFixedRewardAmount());
        }
        if (request.getDeliveryMethod() != null) {
            purchaseRequest.setDeliveryMethod(normalizeRequired(request.getDeliveryMethod(), "交付方式不可空白"));
        }
        if (request.getRequestExpireTime() != null) {
            if (!request.getRequestExpireTime().isAfter(LocalDateTime.now())) {
                throw new RuntimeException("委託期限必須是未來時間");
            }
            purchaseRequest.setRequestExpireTime(request.getRequestExpireTime());
        }
        if (request.getDeliveryDeadlineHours() != null) {
            purchaseRequest.setDeliveryDeadlineHours(request.getDeliveryDeadlineHours());
        }
        if (request.getMinCreditScore() != null) {
            purchaseRequest.setMinCreditScore(request.getMinCreditScore());
        }
        if ("QUOTE".equals(purchaseRequest.getRewardType())) {
            purchaseRequest.setFixedRewardAmount(null);
        }
    }

    private void validatePersistedRequest(PurchaseRequest purchaseRequest) {
        if (purchaseRequest.getItemName() == null || purchaseRequest.getItemName().isBlank()) {
            throw new RuntimeException("商品名稱不可空白");
        }
        validateOptionalEnum(purchaseRequest.getRewardType(), REWARD_TYPES, "酬金模式");
        validateOptionalEnum(purchaseRequest.getDeliveryMethod(), DELIVERY_METHODS, "交付方式");
        if ("FIXED".equals(purchaseRequest.getRewardType())
                && (purchaseRequest.getFixedRewardAmount() == null || purchaseRequest.getFixedRewardAmount() <= 0)) {
            throw new RuntimeException("固定酬金模式必須提供大於 0 的酬金");
        }
        if (purchaseRequest.getDeliveryDeadlineHours() == null || purchaseRequest.getDeliveryDeadlineHours() <= 0) {
            throw new RuntimeException("交貨期限時數必須大於 0");
        }
        if (purchaseRequest.getMinCreditScore() == null || purchaseRequest.getMinCreditScore() < 0) {
            throw new RuntimeException("最低信用分不可小於 0");
        }
    }

    private void extendDeadline(PurchaseRequest purchaseRequest, LocalDateTime newExpireTime) {
        if (newExpireTime == null || !newExpireTime.isAfter(LocalDateTime.now())) {
            throw new RuntimeException("新委託期限必須是未來時間");
        }
        if (!newExpireTime.isAfter(purchaseRequest.getRequestExpireTime())) {
            throw new RuntimeException("新委託期限必須晚於原期限");
        }
        purchaseRequest.setRequestExpireTime(newExpireTime);
    }

    private PurchaseRequest findRequest(Long requestId) {
        return purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("找不到委託單"));
    }

    private PurchaseRequest findOwnedRequest(Long userId, Long requestId) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        if (!purchaseRequest.getRequester().getId().equals(userId)) {
            throw new RuntimeException("只有委託人可以操作此委託單");
        }
        return purchaseRequest;
    }

    private boolean isOrderParty(PurchaseOrder order, Long userId) {
        return order.getRequester().getId().equals(userId) || order.getRunner().getId().equals(userId);
    }

    private PurchaseRequestSummaryResponse toSummaryResponse(PurchaseRequest purchaseRequest) {
        User requester = purchaseRequest.getRequester();
        var details = purchaseDeliveryService.read(purchaseRequest, false);
        return PurchaseRequestSummaryResponse.builder()
                .id(purchaseRequest.getId())
                .requesterId(requester.getId())
                .requesterDisplayName(requester.getDisplayName())
                .requesterProfileImageUrl(requester.getProfileImageUrl())
                .itemName(purchaseRequest.getItemName())
                .imageUrls(toPublicImageUrls(purchaseRequest.getImageUrls()))
                .rewardType(purchaseRequest.getRewardType())
                .fixedRewardAmount(purchaseRequest.getFixedRewardAmount())
                .deliveryMethod(purchaseRequest.getDeliveryMethod())
                .deliveryPublicSummary(purchaseDeliveryService.publicSummary(purchaseRequest.getDeliveryMethod(), details))
                .deliveryDetails(details)
                .requestExpireTime(purchaseRequest.getRequestExpireTime())
                .deliveryDeadlineHours(purchaseRequest.getDeliveryDeadlineHours())
                .minCreditScore(purchaseRequest.getMinCreditScore())
                .status(purchaseRequest.getStatus())
                .createdAt(purchaseRequest.getCreatedAt())
                .build();
    }

    private PurchaseRequestDetailResponse toDetailResponse(PurchaseRequest purchaseRequest, boolean includePrivate) {
        User requester = purchaseRequest.getRequester();
        var details = purchaseDeliveryService.read(purchaseRequest, includePrivate);
        return PurchaseRequestDetailResponse.builder()
                .id(purchaseRequest.getId())
                .requesterId(requester.getId())
                .requesterDisplayName(requester.getDisplayName())
                .requesterProfileImageUrl(requester.getProfileImageUrl())
                .requesterCreditScore(requester.getCreditScore())
                .itemName(purchaseRequest.getItemName())
                .itemDescription(purchaseRequest.getItemDescription())
                .imageUrls(toPublicImageUrls(purchaseRequest.getImageUrls()))
                .rewardType(purchaseRequest.getRewardType())
                .fixedRewardAmount(purchaseRequest.getFixedRewardAmount())
                .deliveryMethod(purchaseRequest.getDeliveryMethod())
                .deliveryPublicSummary(purchaseDeliveryService.publicSummary(purchaseRequest.getDeliveryMethod(), details))
                .deliveryDetails(details)
                .requestExpireTime(purchaseRequest.getRequestExpireTime())
                .deliveryDeadlineHours(purchaseRequest.getDeliveryDeadlineHours())
                .minCreditScore(purchaseRequest.getMinCreditScore())
                .status(purchaseRequest.getStatus())
                .reopenCount(purchaseRequest.getReopenCount())
                .cancelledAt(purchaseRequest.getCancelledAt())
                .expiredAt(purchaseRequest.getExpiredAt())
                .createdAt(purchaseRequest.getCreatedAt())
                .updatedAt(purchaseRequest.getUpdatedAt())
                .build();
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

    private List<String> toStorageImageUrls(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return values.stream()
                .map(value -> toStorageImageUrl(value, normalizedBaseUrl))
                .toList();
    }

    private String toStorageImageUrl(String value, String normalizedBaseUrl) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (!normalizedBaseUrl.isBlank()) {
            if (trimmed.startsWith(normalizedBaseUrl + "/uploads/")) {
                return trimmed.substring((normalizedBaseUrl + "/uploads/").length());
            }
            if (trimmed.startsWith(normalizedBaseUrl + "/images/")) {
                return "campaigns/" + trimmed.substring((normalizedBaseUrl + "/images/").length());
            }
        }
        if (trimmed.startsWith("/uploads/")) {
            return trimmed.substring("/uploads/".length());
        }
        if (trimmed.startsWith("uploads/")) {
            return trimmed.substring("uploads/".length());
        }
        if (trimmed.startsWith("/images/")) {
            return "campaigns/" + trimmed.substring("/images/".length());
        }
        if (trimmed.startsWith("images/")) {
            return "campaigns/" + trimmed.substring("images/".length());
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateOptionalEnum(String value, Set<String> allowedValues, String label) {
        if (value != null && !allowedValues.contains(value)) {
            throw new RuntimeException("不支援的" + label + ": " + value);
        }
    }
}
