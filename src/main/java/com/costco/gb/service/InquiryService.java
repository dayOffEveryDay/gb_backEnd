package com.costco.gb.service;

import com.costco.gb.dto.request.CreateInquiryAnswerRequest;
import com.costco.gb.dto.request.CreateInquiryRequest;
import com.costco.gb.dto.response.*;
import com.costco.gb.entity.*;
import com.costco.gb.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.JoinType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class InquiryService {

    private static final String TYPE_STOCK = "STOCK";
    private static final String TYPE_EXPERIENCE = "EXPERIENCE";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final Set<String> STOCK_STATUSES = Set.of(
            "IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK", "UNKNOWN");

    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;
    private final InquiryFollowRepository inquiryFollowRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final FileService fileService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Page<InquirySummaryResponse> list(String keyword, String type, Integer storeId,
                                             int page, int size, Long currentUserId) {
        validatePage(page, size);
        String normalizedType = normalizeOptional(type);
        validateTypeIfPresent(normalizedType);

        Specification<Inquiry> specification = (root, query, builder) -> builder.conjunction();
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("title")), pattern),
                    builder.like(builder.lower(root.get("productName")), pattern)
            ));
        }
        if (normalizedType != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("type"), normalizedType));
        }
        if (storeId != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("store").get("id"), storeId));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return inquiryRepository.findAll(specification, pageable)
                .map(inquiry -> toSummaryResponse(inquiry, currentUserId));
    }

    @Transactional(readOnly = true)
    public InquiryDetailResponse getDetail(Long inquiryId, Long currentUserId) {
        Inquiry inquiry = findInquiry(inquiryId);
        List<InquiryAnswerResponse> answers = inquiryAnswerRepository
                .findByInquiryId(inquiryId, Sort.by(Sort.Direction.ASC, "createdAt"))
                .stream()
                .map(this::toAnswerResponse)
                .toList();

        return InquiryDetailResponse.builder()
                .id(inquiry.getId())
                .type(inquiry.getType())
                .title(inquiry.getTitle())
                .productName(inquiry.getProductName())
                .content(inquiry.getContent())
                .storeId(inquiry.getStore() == null ? null : inquiry.getStore().getId())
                .storeName(inquiry.getStore() == null ? null : inquiry.getStore().getName())
                .imageUrls(splitImageUrls(inquiry.getImageUrls()))
                .status(inquiry.getStatus())
                .answerCount(answers.size())
                .followCount(inquiryFollowRepository.countByInquiryId(inquiryId))
                .followed(isFollowed(inquiryId, currentUserId))
                .createdAt(inquiry.getCreatedAt())
                .expireTime(inquiry.getExpireTime())
                .author(toAuthorResponse(inquiry.getUser()))
                .answers(answers)
                .build();
    }

    @Transactional(readOnly = true)
    public List<InquirySimilarResponse> findSimilar(String keyword, String type, Integer storeId) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String normalizedType = normalizeOptional(type);
        validateTypeIfPresent(normalizedType);
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";

        Specification<Inquiry> specification = (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("title")), pattern),
                builder.like(builder.lower(root.get("productName")), pattern)
        );
        if (normalizedType != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("type"), normalizedType));
        }
        if (storeId != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("store").get("id"), storeId));
        }

        return inquiryRepository.findAll(specification,
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(inquiry -> InquirySimilarResponse.builder()
                        .id(inquiry.getId())
                        .title(inquiry.getTitle())
                        .answerCount(inquiryAnswerRepository.countByInquiryId(inquiry.getId()))
                        .followCount(inquiryFollowRepository.countByInquiryId(inquiry.getId()))
                        .createdAt(inquiry.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public InquiryDetailResponse create(Long userId, CreateInquiryRequest request, List<MultipartFile> images) {
        String type = normalizeAndValidateType(request.getType());
        Store store = findStore(request.getStoreId());
        List<String> imageUrls = fileService.uploadInquiryImages(images);

        // 庫存問題固定從建立當刻起算 14 天；心得問題不設定到期日。
        Inquiry inquiry = Inquiry.builder()
                .user(findUser(userId))
                .type(type)
                .title(request.getTitle().trim())
                .productName(trimToNull(request.getProductName()))
                .store(store)
                .content(trimToNull(request.getContent()))
                .imageUrls(String.join(",", imageUrls))
                .status(STATUS_OPEN)
                .expireTime(TYPE_STOCK.equals(type) ? LocalDateTime.now().plusDays(14) : null)
                .build();
        Inquiry saved = inquiryRepository.save(inquiry);
        return getDetail(saved.getId(), userId);
    }

    @Transactional
    public InquiryAnswerResponse createAnswer(Long userId, Long inquiryId, CreateInquiryAnswerRequest request) {
        Inquiry inquiry = findInquiry(inquiryId);
        rejectExpiredInquiry(inquiry);
        Store store = findStore(request.getStoreId());
        String stockStatus = normalizeOptional(request.getStockStatus());
        String content = validateAndNormalizeAnswer(inquiry, request, store, stockStatus);

        InquiryAnswer answer = inquiryAnswerRepository.save(InquiryAnswer.builder()
                .inquiry(inquiry)
                .user(findUser(userId))
                .store(store)
                .stockStatus(stockStatus)
                .content(content)
                .build());

        // 新回答通知發問者與關注者，並以使用者 ID 去重及排除回答者本人。
        notifyNewAnswer(inquiry, answer);
        return toAnswerResponse(answer);
    }

    @Transactional
    public InquiryAnswerResponse updateAnswer(Long userId, Long inquiryId, Long answerId,
                                              CreateInquiryAnswerRequest request) {
        Inquiry inquiry = findInquiry(inquiryId);
        rejectExpiredInquiry(inquiry);
        InquiryAnswer answer = inquiryAnswerRepository.findByIdAndInquiryId(answerId, inquiryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "找不到此回答"));
        if (!answer.getUser().getId().equals(userId)) {
            throw new RuntimeException("無權修改此回答");
        }

        Store store = findStore(request.getStoreId());
        String stockStatus = normalizeOptional(request.getStockStatus());
        String content = validateAndNormalizeAnswer(inquiry, request, store, stockStatus);
        answer.setStore(store);
        answer.setStockStatus(stockStatus);
        answer.setContent(content);

        // 立即 flush，確保回應中的 updatedAt 為本次編輯的時間。
        return toAnswerResponse(inquiryAnswerRepository.saveAndFlush(answer));
    }

    @Transactional
    public boolean follow(Long userId, Long inquiryId) {
        Inquiry inquiry = findInquiry(inquiryId);
        rejectExpiredInquiry(inquiry);

        // 重複請求維持成功，讓前端重送時仍具冪等性。
        if (!inquiryFollowRepository.existsByInquiryIdAndUserId(inquiryId, userId)) {
            inquiryFollowRepository.save(InquiryFollow.builder()
                    .inquiry(inquiry)
                    .user(findUser(userId))
                    .build());
        }
        return true;
    }

    @Transactional
    public boolean unfollow(Long userId, Long inquiryId) {
        findInquiry(inquiryId);
        // 未關注時直接完成，維持取消操作的冪等性。
        inquiryFollowRepository.deleteByInquiryIdAndUserId(inquiryId, userId);
        return false;
    }

    @Transactional
    public void expireStockInquiries() {
        List<Inquiry> expiredInquiries = inquiryRepository.findByTypeAndStatusAndExpireTimeLessThanEqual(
                TYPE_STOCK, STATUS_OPEN, LocalDateTime.now());
        for (Inquiry inquiry : expiredInquiries) {
            inquiry.setStatus(STATUS_EXPIRED);

            // 僅在有效期限內完全沒有回答時，通知發問者與所有關注者。
            if (!inquiryAnswerRepository.existsByInquiryId(inquiry.getId())) {
                notifyExpiredWithoutAnswer(inquiry);
            }
        }
    }

    private void notifyNewAnswer(Inquiry inquiry, InquiryAnswer answer) {
        Set<Long> recipientIds = new HashSet<>();
        recipientIds.add(inquiry.getUser().getId());
        inquiryFollowRepository.findByInquiryId(inquiry.getId())
                .forEach(follow -> recipientIds.add(follow.getUser().getId()));
        recipientIds.remove(answer.getUser().getId());

        String content = "你關注的問題有新的回答：「" + inquiry.getTitle() + "」";
        for (Long recipientId : recipientIds) {
            notificationService.sendNotification(findUser(recipientId), "INQUIRY_NEW_ANSWER", inquiry.getId(), content);
        }
    }

    private void notifyExpiredWithoutAnswer(Inquiry inquiry) {
        Set<Long> recipientIds = new HashSet<>();
        recipientIds.add(inquiry.getUser().getId());
        inquiryFollowRepository.findByInquiryId(inquiry.getId())
                .forEach(follow -> recipientIds.add(follow.getUser().getId()));

        String content = "你關注的問題已到期且尚未收到回答：「" + inquiry.getTitle() + "」";
        for (Long recipientId : recipientIds) {
            notificationService.sendNotification(findUser(recipientId), "INQUIRY_EXPIRED_NO_ANSWER", inquiry.getId(), content);
        }
    }

    private InquirySummaryResponse toSummaryResponse(Inquiry inquiry, Long currentUserId) {
        return InquirySummaryResponse.builder()
                .id(inquiry.getId())
                .type(inquiry.getType())
                .title(inquiry.getTitle())
                .productName(inquiry.getProductName())
                .storeId(inquiry.getStore() == null ? null : inquiry.getStore().getId())
                .storeName(inquiry.getStore() == null ? null : inquiry.getStore().getName())
                .imageUrls(splitImageUrls(inquiry.getImageUrls()))
                .status(inquiry.getStatus())
                .answerCount(inquiryAnswerRepository.countByInquiryId(inquiry.getId()))
                .followCount(inquiryFollowRepository.countByInquiryId(inquiry.getId()))
                .followed(isFollowed(inquiry.getId(), currentUserId))
                .createdAt(inquiry.getCreatedAt())
                .expireTime(inquiry.getExpireTime())
                .build();
    }

    private InquiryAnswerResponse toAnswerResponse(InquiryAnswer answer) {
        return InquiryAnswerResponse.builder()
                .id(answer.getId())
                .storeId(answer.getStore() == null ? null : answer.getStore().getId())
                .storeName(answer.getStore() == null ? null : answer.getStore().getName())
                .stockStatus(answer.getStockStatus())
                .content(answer.getContent())
                .createdAt(answer.getCreatedAt())
                .updatedAt(answer.getUpdatedAt())
                .author(toAuthorResponse(answer.getUser()))
                .build();
    }

    private InquiryAuthorResponse toAuthorResponse(User user) {
        return InquiryAuthorResponse.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }

    private boolean isFollowed(Long inquiryId, Long currentUserId) {
        return currentUserId != null && inquiryFollowRepository.existsByInquiryIdAndUserId(inquiryId, currentUserId);
    }

    private void rejectExpiredInquiry(Inquiry inquiry) {
        if (STATUS_EXPIRED.equals(inquiry.getStatus())
                || (inquiry.getExpireTime() != null && !inquiry.getExpireTime().isAfter(LocalDateTime.now()))) {
            inquiry.setStatus(STATUS_EXPIRED);
            throw new RuntimeException("此問題已過期，無法再回答或關注");
        }
    }

    private Inquiry findInquiry(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "找不到此問題"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "找不到使用者"));
    }

    private Store findStore(Integer storeId) {
        if (storeId == null) {
            return null;
        }
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("找不到指定門市"));
    }

    private String validateAndNormalizeAnswer(Inquiry inquiry, CreateInquiryAnswerRequest request,
                                              Store store, String stockStatus) {
        // 庫存狀態只適用於庫存問題；心得回答只允許文字內容。
        if (TYPE_STOCK.equals(inquiry.getType())) {
            if (stockStatus != null && !STOCK_STATUSES.contains(stockStatus)) {
                throw new RuntimeException("庫存狀態不正確");
            }
        } else {
            if (store != null || stockStatus != null) {
                throw new RuntimeException("商品心得回答不可填寫門市或庫存狀態");
            }
            if (!StringUtils.hasText(request.getContent())) {
                throw new RuntimeException("商品心得回答不可空白");
            }
        }

        // 庫存回答可只回報門市或庫存狀態，未填文字時以空字串保存。
        return StringUtils.hasText(request.getContent()) ? request.getContent().trim() : "";
    }

    private void validateTypeIfPresent(String type) {
        if (type != null) {
            normalizeAndValidateType(type);
        }
    }

    private String normalizeAndValidateType(String type) {
        String normalized = normalizeOptional(type);
        if (!TYPE_STOCK.equals(normalized) && !TYPE_EXPERIENCE.equals(normalized)) {
            throw new RuntimeException("問題類型僅能為 STOCK 或 EXPERIENCE");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> splitImageUrls(String imageUrls) {
        if (!StringUtils.hasText(imageUrls)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String imageUrl : imageUrls.split(",")) {
            if (StringUtils.hasText(imageUrl)) {
                result.add(imageUrl.trim());
            }
        }
        return result;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new RuntimeException("分頁參數不正確，size 必須介於 1 到 100");
        }
    }
}
