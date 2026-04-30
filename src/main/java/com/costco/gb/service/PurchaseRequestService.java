package com.costco.gb.service;

import com.costco.gb.dto.request.CancelPurchaseRequestRequest;
import com.costco.gb.dto.request.CreatePurchaseRequestRequest;
import com.costco.gb.dto.request.PurchaseQuoteRequest;
import com.costco.gb.dto.request.UpdatePurchaseRequestRequest;
import com.costco.gb.dto.response.PurchaseQuoteResponse;
import com.costco.gb.dto.response.PurchaseRequestResponse;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.PurchaseRequestQuote;
import com.costco.gb.entity.User;
import com.costco.gb.repository.PurchaseRequestQuoteRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRequestService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_ASSIGNED = "ASSIGNED";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String REWARD_FIXED = "FIXED";
    private static final String REWARD_QUOTE = "QUOTE";
    private static final String QUOTE_PENDING = "PENDING";
    private static final String QUOTE_ACCEPTED = "ACCEPTED";
    private static final String QUOTE_CANCELLED = "CANCELLED";

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestQuoteRepository quoteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final String uploadDir = "uploads/campaigns/";

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public PurchaseRequestResponse create(Long requesterId, CreatePurchaseRequestRequest request) {
        User requester = findUser(requesterId);
        validateRequestFields(request.getProductName(), request.getRewardType(), request.getFixedRewardAmount(), request.getDeliveryMethod());

        List<String> savedFileNames = saveImages(request.getImages(), 0);
        try {
            PurchaseRequest purchaseRequest = PurchaseRequest.builder()
                    .requester(requester)
                    .productName(request.getProductName().trim())
                    .imageUrls(String.join(",", savedFileNames))
                    .rewardType(request.getRewardType().trim().toUpperCase())
                    .fixedRewardAmount(normalizeFixedReward(request.getRewardType(), request.getFixedRewardAmount()))
                    .deliveryMethod(request.getDeliveryMethod().trim().toUpperCase())
                    .requestArea(trimToNull(request.getRequestArea()))
                    .deadlineAt(request.getDeadlineAt())
                    .deliveryTimeType(normalizeDeliveryTimeType(request.getDeliveryTimeType()))
                    .deliveryTimeNote(trimToNull(request.getDeliveryTimeNote()))
                    .minCreditScore(request.getMinCreditScore())
                    .description(trimToNull(request.getDescription()))
                    .status(STATUS_OPEN)
                    .build();

            PurchaseRequest saved = purchaseRequestRepository.save(purchaseRequest);
            return toResponse(saved, requesterId);
        } catch (RuntimeException e) {
            deleteSavedFiles(savedFileNames);
            throw e;
        }
    }

    @Transactional
    public PurchaseRequestResponse update(Long requestId, Long requesterId, UpdatePurchaseRequestRequest request) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        requireRequester(purchaseRequest, requesterId);
        requireOpen(purchaseRequest);

        boolean hasPendingQuotes = quoteRepository.existsByPurchaseRequestIdAndStatus(requestId, QUOTE_PENDING);
        String nextRewardType = request.getRewardType() == null ? purchaseRequest.getRewardType() : request.getRewardType().trim().toUpperCase();
        if (hasPendingQuotes && !purchaseRequest.getRewardType().equals(nextRewardType)) {
            throw new RuntimeException("已有跑腿者報價，不能切換酬金模式");
        }

        String nextProductName = request.getProductName() == null ? purchaseRequest.getProductName() : request.getProductName();
        String nextDeliveryMethod = request.getDeliveryMethod() == null ? purchaseRequest.getDeliveryMethod() : request.getDeliveryMethod();
        BigDecimal nextFixedReward = request.getFixedRewardAmount() == null ? purchaseRequest.getFixedRewardAmount() : request.getFixedRewardAmount();
        validateRequestFields(nextProductName, nextRewardType, nextFixedReward, nextDeliveryMethod);

        purchaseRequest.setProductName(nextProductName.trim());
        purchaseRequest.setRewardType(nextRewardType);
        purchaseRequest.setFixedRewardAmount(normalizeFixedReward(nextRewardType, nextFixedReward));
        purchaseRequest.setDeliveryMethod(nextDeliveryMethod.trim().toUpperCase());
        purchaseRequest.setRequestArea(trimToNull(request.getRequestArea()));
        purchaseRequest.setDeadlineAt(request.getDeadlineAt());
        purchaseRequest.setDeliveryTimeType(normalizeDeliveryTimeType(request.getDeliveryTimeType()));
        purchaseRequest.setDeliveryTimeNote(trimToNull(request.getDeliveryTimeNote()));
        purchaseRequest.setMinCreditScore(request.getMinCreditScore());
        purchaseRequest.setDescription(trimToNull(request.getDescription()));

        return toResponse(purchaseRequestRepository.save(purchaseRequest), requesterId);
    }

    @Transactional
    public PurchaseRequestResponse uploadImages(Long requestId, Long requesterId, List<MultipartFile> images) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        requireRequester(purchaseRequest, requesterId);
        requireOpen(purchaseRequest);

        List<String> currentImages = parseImageNames(purchaseRequest.getImageUrls());
        List<String> savedFileNames = saveImages(images, currentImages.size());
        currentImages.addAll(savedFileNames);
        purchaseRequest.setImageUrls(String.join(",", currentImages));
        return toResponse(purchaseRequestRepository.save(purchaseRequest), requesterId);
    }

    @Transactional
    public PurchaseRequestResponse updateImageOrder(Long requestId, Long requesterId, List<String> imageUrls) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        requireRequester(purchaseRequest, requesterId);

        List<String> currentImages = parseImageNames(purchaseRequest.getImageUrls());
        List<String> nextImages = imageUrls == null ? List.of() : imageUrls.stream()
                .map(this::extractFileName)
                .filter(Objects::nonNull)
                .toList();

        if (currentImages.size() != nextImages.size() || !new HashSet<>(currentImages).equals(new HashSet<>(nextImages))) {
            throw new RuntimeException("圖片排序內容不符合目前託購單圖片");
        }

        purchaseRequest.setImageUrls(String.join(",", nextImages));
        return toResponse(purchaseRequestRepository.save(purchaseRequest), requesterId);
    }

    @Transactional
    public PurchaseRequestResponse deleteImage(Long requestId, Long requesterId, String fileName) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        requireRequester(purchaseRequest, requesterId);
        requireOpen(purchaseRequest);

        String normalizedFileName = extractFileName(fileName);
        List<String> images = new ArrayList<>(parseImageNames(purchaseRequest.getImageUrls()));
        if (!images.remove(normalizedFileName)) {
            throw new RuntimeException("託購單沒有這張圖片");
        }

        purchaseRequest.setImageUrls(String.join(",", images));
        File imageFile = new File(uploadDir, normalizedFileName);
        if (imageFile.exists() && !imageFile.delete()) {
            log.warn("Failed to delete purchase request image {}", normalizedFileName);
        }

        return toResponse(purchaseRequestRepository.save(purchaseRequest), requesterId);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequestResponse> findPublic(String keyword, String rewardType, String deliveryMethod,
                                                    String requestArea, String status, Long currentUserId,
                                                    int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String normalizedStatus = trimToNull(status);
        boolean onlyAvailable = normalizedStatus == null;
        Page<PurchaseRequest> requests = purchaseRequestRepository.findWithFilters(
                trimToNull(keyword),
                normalizeOptional(rewardType),
                normalizeOptional(deliveryMethod),
                trimToNull(requestArea),
                normalizedStatus == null ? null : normalizedStatus.toUpperCase(),
                onlyAvailable,
                LocalDateTime.now(),
                pageable);
        return requests.map(request -> toResponse(request, currentUserId));
    }

    @Transactional(readOnly = true)
    public PurchaseRequestResponse getDetail(Long requestId, Long currentUserId) {
        return toResponse(findRequest(requestId), currentUserId);
    }

    @Transactional
    public PurchaseQuoteResponse createQuote(Long requestId, Long runnerId, PurchaseQuoteRequest request) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        User runner = findUser(runnerId);
        requireQuoteMode(purchaseRequest);
        requireOpen(purchaseRequest);
        requireNotRequester(purchaseRequest, runnerId);
        requireCredit(purchaseRequest, runner);
        validateQuoteAmount(request.getQuoteAmount());

        Optional<PurchaseRequestQuote> existingQuote = quoteRepository.findByPurchaseRequestIdAndRunnerId(requestId, runnerId);
        if (existingQuote.isPresent()) {
            PurchaseRequestQuote quote = existingQuote.get();
            if (!QUOTE_CANCELLED.equals(quote.getStatus())) {
                throw new RuntimeException("你已經對這張託購單報價");
            }
            quote.setQuoteAmount(request.getQuoteAmount());
            quote.setNote(trimToNull(request.getNote()));
            quote.setStatus(QUOTE_PENDING);
            PurchaseRequestQuote savedQuote = quoteRepository.save(quote);
            notificationService.sendNotification(
                    purchaseRequest.getRequester(),
                    "PURCHASE_QUOTED",
                    purchaseRequest.getId(),
                    runner.getDisplayName() + " 對你的託購「" + purchaseRequest.getProductName() + "」報價 " + savedQuote.getQuoteAmount() + " 元"
            );
            return toQuoteResponse(savedQuote);
        }

        PurchaseRequestQuote quote = PurchaseRequestQuote.builder()
                .purchaseRequest(purchaseRequest)
                .runner(runner)
                .quoteAmount(request.getQuoteAmount())
                .note(trimToNull(request.getNote()))
                .status(QUOTE_PENDING)
                .build();
        PurchaseRequestQuote savedQuote = quoteRepository.save(quote);
        notificationService.sendNotification(
                purchaseRequest.getRequester(),
                "PURCHASE_QUOTED",
                purchaseRequest.getId(),
                runner.getDisplayName() + " 對你的託購「" + purchaseRequest.getProductName() + "」報價 " + savedQuote.getQuoteAmount() + " 元"
        );
        return toQuoteResponse(savedQuote);
    }

    @Transactional
    public PurchaseQuoteResponse updateQuote(Long requestId, Long quoteId, Long runnerId, PurchaseQuoteRequest request) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        requireOpen(purchaseRequest);
        PurchaseRequestQuote quote = findQuoteForRequest(requestId, quoteId);
        if (!quote.getRunner().getId().equals(runnerId)) {
            throw new RuntimeException("只能編輯自己的報價");
        }
        if (!QUOTE_PENDING.equals(quote.getStatus())) {
            throw new RuntimeException("只有待確認報價可以編輯");
        }
        validateQuoteAmount(request.getQuoteAmount());
        quote.setQuoteAmount(request.getQuoteAmount());
        quote.setNote(trimToNull(request.getNote()));
        PurchaseRequestQuote savedQuote = quoteRepository.save(quote);
        notificationService.sendNotification(
                purchaseRequest.getRequester(),
                "PURCHASE_QUOTE_UPDATED",
                purchaseRequest.getId(),
                quote.getRunner().getDisplayName() + " 更新了託購「" + purchaseRequest.getProductName() + "」的報價為 " + savedQuote.getQuoteAmount() + " 元"
        );
        return toQuoteResponse(savedQuote);
    }

    @Transactional
    public PurchaseQuoteResponse cancelQuote(Long requestId, Long quoteId, Long runnerId) {
        PurchaseRequestQuote quote = findQuoteForRequest(requestId, quoteId);
        if (!quote.getRunner().getId().equals(runnerId)) {
            throw new RuntimeException("只能取消自己的報價");
        }
        if (!QUOTE_PENDING.equals(quote.getStatus())) {
            throw new RuntimeException("只有待確認報價可以取消");
        }
        quote.setStatus(QUOTE_CANCELLED);
        PurchaseRequestQuote savedQuote = quoteRepository.save(quote);
        notificationService.sendNotification(
                quote.getPurchaseRequest().getRequester(),
                "PURCHASE_QUOTE_CANCELLED",
                quote.getPurchaseRequest().getId(),
                quote.getRunner().getDisplayName() + " 已取消對託購「" + quote.getPurchaseRequest().getProductName() + "」的報價"
        );
        return toQuoteResponse(savedQuote);
    }

    @Transactional(readOnly = true)
    public List<PurchaseQuoteResponse> getQuotes(Long requestId, Long requesterId) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        requireRequester(purchaseRequest, requesterId);
        return quoteRepository.findByPurchaseRequestIdOrderByCreatedAtDesc(requestId).stream()
                .map(this::toQuoteResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseQuoteResponse getMyQuote(Long requestId, Long runnerId) {
        return quoteRepository.findByPurchaseRequestIdAndRunnerId(requestId, runnerId)
                .map(this::toQuoteResponse)
                .orElseThrow(() -> new RuntimeException("你尚未對這張託購單報價"));
    }

    @Transactional
    public PurchaseRequestResponse acceptDirectly(Long requestId, Long runnerId) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        User runner = findUser(runnerId);
        if (!REWARD_FIXED.equals(purchaseRequest.getRewardType())) {
            throw new RuntimeException("這張託購單不是固定酬金模式");
        }
        requireOpen(purchaseRequest);
        requireNotRequester(purchaseRequest, runnerId);
        requireCredit(purchaseRequest, runner);

        purchaseRequest.setAssignedRunner(runner);
        purchaseRequest.setStatus(STATUS_ASSIGNED);
        PurchaseRequest savedRequest = purchaseRequestRepository.save(purchaseRequest);
        notificationService.sendNotification(
                purchaseRequest.getRequester(),
                "PURCHASE_ACCEPTED",
                purchaseRequest.getId(),
                runner.getDisplayName() + " 已承接你的託購「" + purchaseRequest.getProductName() + "」"
        );
        return toResponse(savedRequest, runnerId);
    }

    @Transactional
    public PurchaseRequestResponse acceptQuote(Long requestId, Long quoteId, Long requesterId) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        requireRequester(purchaseRequest, requesterId);
        requireOpen(purchaseRequest);
        requireQuoteMode(purchaseRequest);

        PurchaseRequestQuote quote = findQuoteForRequest(requestId, quoteId);
        if (!QUOTE_PENDING.equals(quote.getStatus())) {
            throw new RuntimeException("只能接受待確認報價");
        }
        requireCredit(purchaseRequest, quote.getRunner());

        List<PurchaseRequestQuote> rejectedQuotes = quoteRepository.findByPurchaseRequestIdOrderByCreatedAtDesc(requestId).stream()
                .filter(candidate -> !candidate.getId().equals(quoteId))
                .filter(candidate -> QUOTE_PENDING.equals(candidate.getStatus()))
                .toList();

        quote.setStatus(QUOTE_ACCEPTED);
        quoteRepository.save(quote);
        quoteRepository.rejectOtherPendingQuotes(requestId, quoteId);

        purchaseRequest.setAcceptedQuote(quote);
        purchaseRequest.setAssignedRunner(quote.getRunner());
        purchaseRequest.setStatus(STATUS_ASSIGNED);
        PurchaseRequest savedRequest = purchaseRequestRepository.save(purchaseRequest);

        notificationService.sendNotification(
                quote.getRunner(),
                "PURCHASE_QUOTE_ACCEPTED",
                purchaseRequest.getId(),
                "委託人已接受你對「" + purchaseRequest.getProductName() + "」的報價"
        );
        for (PurchaseRequestQuote rejectedQuote : rejectedQuotes) {
            notificationService.sendNotification(
                    rejectedQuote.getRunner(),
                    "PURCHASE_QUOTE_REJECTED",
                    purchaseRequest.getId(),
                    "委託人已選擇其他跑腿者承接「" + purchaseRequest.getProductName() + "」"
            );
        }

        return toResponse(savedRequest, requesterId);
    }

    @Transactional
    public PurchaseRequestResponse deliver(Long requestId, Long runnerId) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        if (purchaseRequest.getAssignedRunner() == null || !purchaseRequest.getAssignedRunner().getId().equals(runnerId)) {
            throw new RuntimeException("只有承接的跑腿者可以標記已交付");
        }
        if (!STATUS_ASSIGNED.equals(purchaseRequest.getStatus())) {
            throw new RuntimeException("只有已成立的託購單可以標記已交付");
        }
        purchaseRequest.setStatus(STATUS_DELIVERED);
        purchaseRequest.setDeliveredAt(LocalDateTime.now());
        PurchaseRequest savedRequest = purchaseRequestRepository.save(purchaseRequest);
        notificationService.sendNotification(
                purchaseRequest.getRequester(),
                "PURCHASE_DELIVERED",
                purchaseRequest.getId(),
                purchaseRequest.getAssignedRunner().getDisplayName() + " 已標記託購「" + purchaseRequest.getProductName() + "」為已交付"
        );
        return toResponse(savedRequest, runnerId);
    }

    @Transactional
    public PurchaseRequestResponse complete(Long requestId, Long requesterId) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        requireRequester(purchaseRequest, requesterId);
        if (!STATUS_ASSIGNED.equals(purchaseRequest.getStatus()) && !STATUS_DELIVERED.equals(purchaseRequest.getStatus())) {
            throw new RuntimeException("只有已成立或已交付的託購單可以完成");
        }
        purchaseRequest.setStatus(STATUS_COMPLETED);
        purchaseRequest.setCompletedAt(LocalDateTime.now());
        PurchaseRequest savedRequest = purchaseRequestRepository.save(purchaseRequest);
        if (purchaseRequest.getAssignedRunner() != null) {
            notificationService.sendNotification(
                    purchaseRequest.getAssignedRunner(),
                    "PURCHASE_COMPLETED",
                    purchaseRequest.getId(),
                    "委託人已確認完成託購「" + purchaseRequest.getProductName() + "」"
            );
        }
        return toResponse(savedRequest, requesterId);
    }

    @Transactional
    public PurchaseRequestResponse cancel(Long requestId, Long requesterId, CancelPurchaseRequestRequest request) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        requireRequester(purchaseRequest, requesterId);
        requireOpen(purchaseRequest);
        List<PurchaseRequestQuote> pendingQuotes = quoteRepository.findByPurchaseRequestIdOrderByCreatedAtDesc(requestId).stream()
                .filter(quote -> QUOTE_PENDING.equals(quote.getStatus()))
                .toList();
        purchaseRequest.setStatus(STATUS_CANCELLED);
        purchaseRequest.setCancelReason(request == null ? null : trimToNull(request.getReason()));
        PurchaseRequest savedRequest = purchaseRequestRepository.save(purchaseRequest);
        for (PurchaseRequestQuote quote : pendingQuotes) {
            notificationService.sendNotification(
                    quote.getRunner(),
                    "PURCHASE_CANCELLED",
                    purchaseRequest.getId(),
                    "委託人已取消託購「" + purchaseRequest.getProductName() + "」"
            );
        }
        return toResponse(savedRequest, requesterId);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequestResponse> getMyCreated(Long userId, Pageable pageable) {
        return purchaseRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId, pageable)
                .map(request -> toResponse(request, userId));
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequestResponse> getMyAssigned(Long userId, Pageable pageable) {
        return purchaseRequestRepository.findByAssignedRunnerIdOrderByCreatedAtDesc(userId, pageable)
                .map(request -> toResponse(request, userId));
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequestResponse> getMyQuoted(Long userId, Pageable pageable) {
        return purchaseRequestRepository.findQuotedByRunnerId(userId, pageable)
                .map(request -> toResponse(request, userId));
    }

    private PurchaseRequest findRequest(Long requestId) {
        return purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("找不到託購單"));
    }

    private PurchaseRequestQuote findQuoteForRequest(Long requestId, Long quoteId) {
        PurchaseRequestQuote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("找不到報價"));
        if (!quote.getPurchaseRequest().getId().equals(requestId)) {
            throw new RuntimeException("報價不屬於這張託購單");
        }
        return quote;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("找不到使用者"));
    }

    private void requireRequester(PurchaseRequest purchaseRequest, Long userId) {
        if (!purchaseRequest.getRequester().getId().equals(userId)) {
            throw new RuntimeException("只有委託人可以執行這個操作");
        }
    }

    private void requireNotRequester(PurchaseRequest purchaseRequest, Long userId) {
        if (purchaseRequest.getRequester().getId().equals(userId)) {
            throw new RuntimeException("不能承接或報價自己的託購單");
        }
    }

    private void requireOpen(PurchaseRequest purchaseRequest) {
        if (!STATUS_OPEN.equals(purchaseRequest.getStatus())) {
            throw new RuntimeException("託購單目前不是開放狀態");
        }
        if (purchaseRequest.getDeadlineAt() != null && purchaseRequest.getDeadlineAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("託購單已超過委託期限");
        }
    }

    private void requireQuoteMode(PurchaseRequest purchaseRequest) {
        if (!REWARD_QUOTE.equals(purchaseRequest.getRewardType())) {
            throw new RuntimeException("這張託購單不是報價模式");
        }
    }

    private void requireCredit(PurchaseRequest purchaseRequest, User runner) {
        Integer minCreditScore = purchaseRequest.getMinCreditScore();
        if (minCreditScore != null && runner.getCreditScore() < minCreditScore) {
            throw new RuntimeException("信用分未達託購單限制");
        }
    }

    private void validateRequestFields(String productName, String rewardType, BigDecimal fixedRewardAmount, String deliveryMethod) {
        if (productName == null || productName.trim().isEmpty()) {
            throw new RuntimeException("商品名稱必填");
        }
        String normalizedRewardType = rewardType == null ? null : rewardType.trim().toUpperCase();
        if (!REWARD_FIXED.equals(normalizedRewardType) && !REWARD_QUOTE.equals(normalizedRewardType)) {
            throw new RuntimeException("酬金模式必須是 FIXED 或 QUOTE");
        }
        if (REWARD_FIXED.equals(normalizedRewardType) && (fixedRewardAmount == null || fixedRewardAmount.compareTo(BigDecimal.ZERO) < 0)) {
            throw new RuntimeException("固定酬金不可為空或小於 0");
        }
        if (deliveryMethod == null || deliveryMethod.trim().isEmpty()) {
            throw new RuntimeException("交貨方式必填");
        }
    }

    private void validateQuoteAmount(BigDecimal quoteAmount) {
        if (quoteAmount == null || quoteAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("報價金額不可為空或小於 0");
        }
    }

    private BigDecimal normalizeFixedReward(String rewardType, BigDecimal fixedRewardAmount) {
        return REWARD_FIXED.equals(rewardType == null ? null : rewardType.trim().toUpperCase()) ? fixedRewardAmount : null;
    }

    private String normalizeDeliveryTimeType(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? "DISCUSS" : normalized;
    }

    private String normalizeOptional(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> saveImages(List<MultipartFile> images, int currentCount) {
        if (images == null || images.isEmpty()) {
            return new ArrayList<>();
        }
        if (currentCount + images.size() > 3) {
            throw new RuntimeException("商品圖片最多 3 張");
        }

        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("無法建立圖片上傳目錄");
        }

        List<String> savedFileNames = new ArrayList<>();
        try {
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String originalFilename = file.getOriginalFilename();
                String extension = (originalFilename != null && originalFilename.contains("."))
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";
                String newFileName = UUID.randomUUID() + extension;
                File destinationFile = new File(dir, newFileName);
                Thumbnails.of(file.getInputStream())
                        .size(1080, 1080)
                        .toFile(destinationFile);
                savedFileNames.add(newFileName);
            }
            return savedFileNames;
        } catch (Exception e) {
            deleteSavedFiles(savedFileNames);
            throw new RuntimeException("圖片上傳失敗: " + e.getMessage());
        }
    }

    private void deleteSavedFiles(List<String> savedFileNames) {
        for (String fileName : savedFileNames) {
            File fileToDelete = new File(uploadDir, fileName);
            if (fileToDelete.exists() && !fileToDelete.delete()) {
                log.warn("Failed to delete purchase request image {}", fileName);
            }
        }
    }

    private List<String> parseImageNames(String imageUrls) {
        if (imageUrls == null || imageUrls.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(imageUrls.split(","))
                .map(this::extractFileName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String extractFileName(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        int slashIndex = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        return slashIndex >= 0 ? trimmed.substring(slashIndex + 1) : trimmed;
    }

    private PurchaseRequestResponse toResponse(PurchaseRequest purchaseRequest, Long currentUserId) {
        long quoteCount = quoteRepository.countByPurchaseRequestIdAndStatus(purchaseRequest.getId(), QUOTE_PENDING);
        User currentUser = currentUserId == null ? null : userRepository.findById(currentUserId).orElse(null);
        String blockedReason = getActBlockedReason(purchaseRequest, currentUser);
        boolean isOpen = STATUS_OPEN.equals(purchaseRequest.getStatus());
        boolean isRequester = currentUserId != null && purchaseRequest.getRequester().getId().equals(currentUserId);

        return PurchaseRequestResponse.builder()
                .id(purchaseRequest.getId())
                .productName(purchaseRequest.getProductName())
                .imageUrls(parseImageNames(purchaseRequest.getImageUrls()).stream()
                        .map(fileName -> baseUrl + "/images/" + fileName)
                        .toList())
                .rewardType(purchaseRequest.getRewardType())
                .fixedRewardAmount(purchaseRequest.getFixedRewardAmount())
                .quoteCount(quoteCount)
                .deliveryMethod(purchaseRequest.getDeliveryMethod())
                .requestArea(purchaseRequest.getRequestArea())
                .deadlineAt(purchaseRequest.getDeadlineAt())
                .deliveryTimeType(purchaseRequest.getDeliveryTimeType())
                .deliveryTimeNote(purchaseRequest.getDeliveryTimeNote())
                .minCreditScore(purchaseRequest.getMinCreditScore())
                .description(purchaseRequest.getDescription())
                .status(purchaseRequest.getStatus())
                .requester(toUserSummary(purchaseRequest.getRequester()))
                .assignedRunner(toUserSummary(purchaseRequest.getAssignedRunner()))
                .acceptedQuoteId(purchaseRequest.getAcceptedQuote() == null ? null : purchaseRequest.getAcceptedQuote().getId())
                .chatRoomId(purchaseRequest.getChatRoomId())
                .canQuote(isOpen && REWARD_QUOTE.equals(purchaseRequest.getRewardType()) && blockedReason == null)
                .canAcceptDirectly(isOpen && REWARD_FIXED.equals(purchaseRequest.getRewardType()) && blockedReason == null)
                .canEdit(isOpen && isRequester)
                .actBlockedReason(blockedReason)
                .deliveredAt(purchaseRequest.getDeliveredAt())
                .completedAt(purchaseRequest.getCompletedAt())
                .createdAt(purchaseRequest.getCreatedAt())
                .updatedAt(purchaseRequest.getUpdatedAt())
                .build();
    }

    private String getActBlockedReason(PurchaseRequest purchaseRequest, User currentUser) {
        if (currentUser == null) {
            return "LOGIN_REQUIRED";
        }
        if (purchaseRequest.getRequester().getId().equals(currentUser.getId())) {
            return "IS_REQUESTER";
        }
        Integer minCreditScore = purchaseRequest.getMinCreditScore();
        if (minCreditScore != null && currentUser.getCreditScore() < minCreditScore) {
            return "CREDIT_SCORE_TOO_LOW";
        }
        return null;
    }

    private PurchaseRequestResponse.UserSummary toUserSummary(User user) {
        if (user == null) {
            return null;
        }
        return PurchaseRequestResponse.UserSummary.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .creditScore(user.getCreditScore())
                .build();
    }

    private PurchaseQuoteResponse toQuoteResponse(PurchaseRequestQuote quote) {
        return PurchaseQuoteResponse.builder()
                .id(quote.getId())
                .purchaseRequestId(quote.getPurchaseRequest().getId())
                .quoteAmount(quote.getQuoteAmount())
                .note(quote.getNote())
                .status(quote.getStatus())
                .runner(toUserSummary(quote.getRunner()))
                .createdAt(quote.getCreatedAt())
                .updatedAt(quote.getUpdatedAt())
                .build();
    }
}
