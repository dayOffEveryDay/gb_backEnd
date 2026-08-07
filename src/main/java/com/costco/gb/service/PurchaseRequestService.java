package com.costco.gb.service;

import com.costco.gb.dto.request.CreatePurchaseRequestRequest;
import com.costco.gb.dto.request.MeetupRangeRequest;
import com.costco.gb.dto.request.UpdatePurchaseRequestRequest;
import com.costco.gb.dto.response.MeetupRangeResponse;
import com.costco.gb.dto.response.PurchaseRequestDetailResponse;
import com.costco.gb.dto.response.PurchaseRequestSummaryResponse;
import com.costco.gb.entity.PurchaseOrder;
import com.costco.gb.entity.PurchaseQuote;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.PurchaseRequestMeetupRange;
import com.costco.gb.entity.User;
import com.costco.gb.entity.UserDeliveryProfile;
import com.costco.gb.repository.PurchaseOrderRepository;
import com.costco.gb.repository.PurchaseQuoteRepository;
import com.costco.gb.repository.PurchaseRequestMeetupRangeRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import com.costco.gb.repository.UserDeliveryProfileRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchaseRequestService {

    private static final String FACE_TO_FACE = "FACE_TO_FACE";
    private static final Set<String> REWARD_TYPES = Set.of("FIXED", "QUOTE");
    private static final Set<String> DELIVERY_METHODS = Set.of(
            FACE_TO_FACE, "HOME_DELIVERY", "STORE_TO_STORE"
    );
    private static final Set<String> REQUEST_STATUSES = Set.of(
            "OPEN", "QUOTING", "ORDERED", "EXPIRED", "CANCELLED"
    );
    private static final Set<String> MEETUP_LOCATION_TYPES = Set.of(
            "TRANSIT_RANGE", "LANDMARK", "ROAD", "DISTRICT", "CUSTOM"
    );

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestMeetupRangeRepository meetupRangeRepository;
    private final UserDeliveryProfileRepository profileRepository;
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

        // 先建立委託單，取得主鍵後才能保存面交快照。
        PurchaseRequest purchaseRequest = PurchaseRequest.builder()
                .requester(requester)
                .itemName(request.getItemName().trim())
                .itemDescription(request.getItemDescription())
                .imageUrls(toStorageImageUrls(request.getImageUrls()))
                .rewardType(rewardType)
                .fixedRewardAmount("FIXED".equals(rewardType) ? request.getFixedRewardAmount() : null)
                .requesterCity(request.getRequesterCity().trim())
                .requesterDistrict(request.getRequesterDistrict())
                .deliveryMethod(deliveryMethod)
                .deliveryPublicSummary(request.getDeliveryPublicSummary().trim())
                .deliveryPrivateDetail(FACE_TO_FACE.equals(deliveryMethod) ? null : request.getDeliveryPrivateDetail())
                .requestExpireTime(request.getRequestExpireTime())
                .deliveryDeadlineHours(request.getDeliveryDeadlineHours())
                .minCreditScore(request.getMinCreditScore() == null ? 0 : request.getMinCreditScore())
                .status("OPEN")
                .build();
        purchaseRequestRepository.save(purchaseRequest);

        // 面交資料必須複製成委託單快照，避免常用資料日後修改而改變既有委託。
        if (FACE_TO_FACE.equals(deliveryMethod)) {
            replaceMeetupRanges(
                    purchaseRequest,
                    requester,
                    request.getSelectedProfileIds(),
                    request.getManualMeetupRanges(),
                    Boolean.TRUE.equals(request.getSaveManualToProfile())
            );
        } else if (Boolean.TRUE.equals(request.getSaveDeliveryToProfile())) {
            saveDeliveryProfile(requester, purchaseRequest);
        }

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
        List<PurchaseRequestMeetupRange> ranges = FACE_TO_FACE.equals(purchaseRequest.getDeliveryMethod())
                ? meetupRangeRepository.findByPurchaseRequestIdOrderById(requestId)
                : List.of();

        // 私密交付資料只有訂單成立後的交易雙方可讀取；公開詳情一律隱藏。
        Map<String, Object> privateDetail = null;
        if (currentUserId != null) {
            privateDetail = orderRepository.findFirstByPurchaseRequestIdOrderByCreatedAtDesc(requestId)
                    .filter(order -> isOrderParty(order, currentUserId))
                    .map(order -> purchaseRequest.getDeliveryPrivateDetail())
                    .orElse(null);
        }
        return toDetailResponse(purchaseRequest, ranges, privateDetail);
    }

    @Transactional
    public PurchaseRequestDetailResponse update(Long userId, Long requestId, UpdatePurchaseRequestRequest request) {
        PurchaseRequest purchaseRequest = findOwnedRequest(userId, requestId);
        if (!Set.of("OPEN", "QUOTING").contains(purchaseRequest.getStatus())) {
            throw new RuntimeException("目前委託單狀態不可修改");
        }

        boolean hasActiveQuote = quoteRepository.existsByPurchaseRequestIdAndStatus(requestId, "ACTIVE");
        if ("QUOTING".equals(purchaseRequest.getStatus()) || hasActiveQuote) {
            if (request.hasNonExpiryChanges() || request.getRequestExpireTime() == null) {
                throw new RuntimeException("已有有效報價後只允許延長委託期限");
            }
            extendDeadline(purchaseRequest, request.getRequestExpireTime());
        } else {
            applyOpenRequestChanges(purchaseRequest, request);
            validatePersistedRequest(purchaseRequest);

            boolean meetupChanged = request.getSelectedProfileIds() != null
                    || request.getManualMeetupRanges() != null;
            if (FACE_TO_FACE.equals(purchaseRequest.getDeliveryMethod())) {
                if (meetupChanged) {
                    replaceMeetupRanges(
                            purchaseRequest,
                            purchaseRequest.getRequester(),
                            request.getSelectedProfileIds(),
                            request.getManualMeetupRanges(),
                            Boolean.TRUE.equals(request.getSaveManualToProfile())
                    );
                } else if (meetupRangeRepository.countByPurchaseRequestId(requestId) == 0) {
                    throw new RuntimeException("面交委託至少需要一筆面交範圍");
                }
                purchaseRequest.setDeliveryPrivateDetail(null);
            } else {
                meetupRangeRepository.deleteByPurchaseRequestId(requestId);
                if (Boolean.TRUE.equals(request.getSaveDeliveryToProfile())) {
                    saveDeliveryProfile(purchaseRequest.getRequester(), purchaseRequest);
                }
            }
        }

        purchaseRequestRepository.save(purchaseRequest);
        List<PurchaseRequestMeetupRange> ranges = FACE_TO_FACE.equals(purchaseRequest.getDeliveryMethod())
                ? meetupRangeRepository.findByPurchaseRequestIdOrderById(requestId)
                : List.of();
        return toDetailResponse(purchaseRequest, ranges, null);
    }

    @Transactional
    public void cancel(Long userId, Long requestId) {
        PurchaseRequest purchaseRequest = findOwnedRequest(userId, requestId);
        if (!Set.of("OPEN", "QUOTING").contains(purchaseRequest.getStatus())) {
            throw new RuntimeException("只有開放中或報價中的委託單可以取消");
        }

        // 取消委託時一併使所有有效報價失效，不產生信用扣分。
        purchaseRequest.setStatus("CANCELLED");
        purchaseRequest.setCancelledAt(LocalDateTime.now());
        quoteRepository.expireActiveQuotes(requestId);
        purchaseRequestRepository.save(purchaseRequest);
    }

    @Transactional
    public PurchaseRequestDetailResponse extend(Long userId, Long requestId, LocalDateTime newExpireTime) {
        PurchaseRequest purchaseRequest = findOwnedRequest(userId, requestId);
        if (!Set.of("OPEN", "QUOTING").contains(purchaseRequest.getStatus())) {
            throw new RuntimeException("目前委託單狀態不可延長期限");
        }
        extendDeadline(purchaseRequest, newExpireTime);
        purchaseRequestRepository.save(purchaseRequest);

        List<PurchaseRequestMeetupRange> ranges = FACE_TO_FACE.equals(purchaseRequest.getDeliveryMethod())
                ? meetupRangeRepository.findByPurchaseRequestIdOrderById(requestId)
                : List.of();
        return toDetailResponse(purchaseRequest, ranges, null);
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

        boolean hasSelectedProfiles = request.getSelectedProfileIds() != null
                && !request.getSelectedProfileIds().isEmpty();
        boolean hasManualRanges = request.getManualMeetupRanges() != null
                && !request.getManualMeetupRanges().isEmpty();
        if (FACE_TO_FACE.equals(deliveryMethod) && !hasSelectedProfiles && !hasManualRanges) {
            throw new RuntimeException("面交委託至少需要一筆面交範圍");
        }
        if (!FACE_TO_FACE.equals(deliveryMethod)
                && (request.getDeliveryPrivateDetail() == null || request.getDeliveryPrivateDetail().isEmpty())) {
            throw new RuntimeException("宅配或店到店必須提供私密交付資料");
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
        if (request.getRequesterCity() != null) {
            purchaseRequest.setRequesterCity(request.getRequesterCity().trim());
        }
        if (request.getRequesterDistrict() != null) {
            purchaseRequest.setRequesterDistrict(request.getRequesterDistrict());
        }
        if (request.getDeliveryMethod() != null) {
            purchaseRequest.setDeliveryMethod(normalizeRequired(request.getDeliveryMethod(), "交付方式不可空白"));
        }
        if (request.getDeliveryPublicSummary() != null) {
            purchaseRequest.setDeliveryPublicSummary(request.getDeliveryPublicSummary().trim());
        }
        if (request.getDeliveryPrivateDetail() != null) {
            purchaseRequest.setDeliveryPrivateDetail(request.getDeliveryPrivateDetail());
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
        if (!FACE_TO_FACE.equals(purchaseRequest.getDeliveryMethod())
                && (purchaseRequest.getDeliveryPrivateDetail() == null
                || purchaseRequest.getDeliveryPrivateDetail().isEmpty())) {
            throw new RuntimeException("宅配或店到店必須提供私密交付資料");
        }
    }

    private void replaceMeetupRanges(
            PurchaseRequest purchaseRequest,
            User requester,
            List<Long> selectedProfileIds,
            List<MeetupRangeRequest> manualRanges,
            boolean saveManualToProfile
    ) {
        List<Long> profileIds = selectedProfileIds == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(selectedProfileIds));
        List<MeetupRangeRequest> ranges = manualRanges == null ? List.of() : manualRanges;
        if (profileIds.isEmpty() && ranges.isEmpty()) {
            throw new RuntimeException("面交委託至少需要一筆面交範圍");
        }

        List<UserDeliveryProfile> profiles = profileRepository.findAllById(profileIds);
        if (profiles.size() != profileIds.size()) {
            throw new RuntimeException("部分常用交付資料不存在");
        }
        for (UserDeliveryProfile profile : profiles) {
            if (!profile.getUser().getId().equals(requester.getId())) {
                throw new RuntimeException("不可使用其他使用者的常用交付資料");
            }
            if (!FACE_TO_FACE.equals(profile.getProfileType())) {
                throw new RuntimeException("面交委託只能選用面交類型的常用資料");
            }
        }
        for (MeetupRangeRequest range : ranges) {
            validateMeetupRange(range);
        }

        meetupRangeRepository.deleteByPurchaseRequestId(purchaseRequest.getId());
        List<PurchaseRequestMeetupRange> snapshots = new ArrayList<>();
        profiles.forEach(profile -> snapshots.add(snapshotFromProfile(purchaseRequest, profile)));
        ranges.forEach(range -> snapshots.add(snapshotFromManual(purchaseRequest, range)));
        meetupRangeRepository.saveAll(snapshots);

        // 使用者明確選擇保存時，才將手動面交資料另存為可重複使用的模板。
        if (saveManualToProfile) {
            ranges.forEach(range -> profileRepository.save(profileFromManual(requester, range)));
        }
    }

    private void validateMeetupRange(MeetupRangeRequest range) {
        if (range == null) {
            throw new RuntimeException("面交範圍不可為空");
        }
        String locationType = normalizeRequired(range.getLocationType(), "面交地點類型不可空白");
        if (!MEETUP_LOCATION_TYPES.contains(locationType)) {
            throw new RuntimeException("不支援的面交地點類型: " + locationType);
        }
        if (range.getDisplayText() == null || range.getDisplayText().isBlank()) {
            throw new RuntimeException("面交顯示文字不可空白");
        }
    }

    private PurchaseRequestMeetupRange snapshotFromProfile(
            PurchaseRequest purchaseRequest,
            UserDeliveryProfile profile
    ) {
        return PurchaseRequestMeetupRange.builder()
                .purchaseRequest(purchaseRequest)
                .sourceProfile(profile)
                .locationType(profile.getLocationType())
                .city(profile.getCity())
                .district(profile.getDistrict())
                .locationName(profile.getLocationName())
                .rangeStart(profile.getRangeStart())
                .rangeEnd(profile.getRangeEnd())
                .displayText(profile.getDisplayText())
                .detail(profile.getDetail())
                .build();
    }

    private PurchaseRequestMeetupRange snapshotFromManual(
            PurchaseRequest purchaseRequest,
            MeetupRangeRequest range
    ) {
        return PurchaseRequestMeetupRange.builder()
                .purchaseRequest(purchaseRequest)
                .locationType(normalizeRequired(range.getLocationType(), "面交地點類型不可空白"))
                .city(range.getCity())
                .district(range.getDistrict())
                .locationName(range.getLocationName())
                .rangeStart(range.getRangeStart())
                .rangeEnd(range.getRangeEnd())
                .displayText(range.getDisplayText().trim())
                .detail(range.getDetail())
                .latitude(range.getLatitude())
                .longitude(range.getLongitude())
                .build();
    }

    private UserDeliveryProfile profileFromManual(User requester, MeetupRangeRequest range) {
        return UserDeliveryProfile.builder()
                .user(requester)
                .profileType(FACE_TO_FACE)
                .locationType(normalizeRequired(range.getLocationType(), "面交地點類型不可空白"))
                .city(range.getCity())
                .district(range.getDistrict())
                .locationName(range.getLocationName())
                .rangeStart(range.getRangeStart())
                .rangeEnd(range.getRangeEnd())
                .displayText(range.getDisplayText().trim())
                .detail(range.getDetail())
                .isDefault(false)
                .build();
    }

    private void saveDeliveryProfile(User requester, PurchaseRequest purchaseRequest) {
        UserDeliveryProfile profile = UserDeliveryProfile.builder()
                .user(requester)
                .profileType(purchaseRequest.getDeliveryMethod())
                .locationType("HOME_DELIVERY".equals(purchaseRequest.getDeliveryMethod()) ? "ADDRESS" : "STORE")
                .city(purchaseRequest.getRequesterCity())
                .district(purchaseRequest.getRequesterDistrict())
                .displayText(purchaseRequest.getDeliveryPublicSummary())
                .detail(purchaseRequest.getDeliveryPrivateDetail())
                .isDefault(false)
                .build();
        profileRepository.save(profile);
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
        return PurchaseRequestSummaryResponse.builder()
                .id(purchaseRequest.getId())
                .requesterId(requester.getId())
                .requesterDisplayName(requester.getDisplayName())
                .requesterProfileImageUrl(requester.getProfileImageUrl())
                .itemName(purchaseRequest.getItemName())
                .imageUrls(toPublicImageUrls(purchaseRequest.getImageUrls()))
                .rewardType(purchaseRequest.getRewardType())
                .fixedRewardAmount(purchaseRequest.getFixedRewardAmount())
                .requesterCity(purchaseRequest.getRequesterCity())
                .requesterDistrict(purchaseRequest.getRequesterDistrict())
                .deliveryMethod(purchaseRequest.getDeliveryMethod())
                .deliveryPublicSummary(purchaseRequest.getDeliveryPublicSummary())
                .requestExpireTime(purchaseRequest.getRequestExpireTime())
                .deliveryDeadlineHours(purchaseRequest.getDeliveryDeadlineHours())
                .minCreditScore(purchaseRequest.getMinCreditScore())
                .status(purchaseRequest.getStatus())
                .createdAt(purchaseRequest.getCreatedAt())
                .build();
    }

    private PurchaseRequestDetailResponse toDetailResponse(
            PurchaseRequest purchaseRequest,
            List<PurchaseRequestMeetupRange> ranges,
            Map<String, Object> privateDetail
    ) {
        User requester = purchaseRequest.getRequester();
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
                .requesterCity(purchaseRequest.getRequesterCity())
                .requesterDistrict(purchaseRequest.getRequesterDistrict())
                .deliveryMethod(purchaseRequest.getDeliveryMethod())
                .deliveryPublicSummary(purchaseRequest.getDeliveryPublicSummary())
                .deliveryPrivateDetail(privateDetail)
                .meetupRanges(ranges.stream().map(this::toMeetupRangeResponse).toList())
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
