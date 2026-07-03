package com.costco.gb.service;

import com.costco.gb.dto.request.PurchaseQuoteRequest;
import com.costco.gb.dto.response.PurchaseOrderResponse;
import com.costco.gb.dto.response.PurchaseQuoteResponse;
import com.costco.gb.entity.PurchaseOrder;
import com.costco.gb.entity.PurchaseOrderEvent;
import com.costco.gb.entity.PurchaseQuote;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.User;
import com.costco.gb.repository.BlockRepository;
import com.costco.gb.repository.PurchaseOrderEventRepository;
import com.costco.gb.repository.PurchaseOrderRepository;
import com.costco.gb.repository.PurchaseQuoteRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchaseQuoteService {

    private static final String ACTIVE = "ACTIVE";
    private static final Set<String> QUOTEABLE_REQUEST_STATUSES = Set.of("OPEN", "QUOTING");

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseQuoteRepository quoteRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderEventRepository orderEventRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final NotificationService notificationService;
    private final PurchaseChatService purchaseChatService;

    @Transactional
    public PurchaseQuoteResponse createQuote(Long userId, Long requestId, PurchaseQuoteRequest request) {
        validateQuotePayload(request);
        PurchaseRequest purchaseRequest = lockRequest(requestId);
        validateQuoteableRequest(purchaseRequest);
        User quoter = findUser(userId);
        validateCandidate(purchaseRequest, quoter);

        // 資料庫對委託單與報價者設有唯一鍵；撤回後也不可另建第二筆紀錄。
        if (quoteRepository.existsByPurchaseRequestIdAndQuoterId(requestId, userId)) {
            throw new RuntimeException("您已對此委託單建立過報價");
        }

        PurchaseQuote quote = PurchaseQuote.builder()
                .purchaseRequest(purchaseRequest)
                .quoter(quoter)
                .quoteAmount(request.getQuoteAmount())
                .quoteNote(request.getQuoteNote())
                .status(ACTIVE)
                .build();
        quoteRepository.save(quote);

        if ("OPEN".equals(purchaseRequest.getStatus())) {
            purchaseRequest.setStatus("QUOTING");
            purchaseRequestRepository.save(purchaseRequest);
        }

        notificationService.sendNotification(
                purchaseRequest.getRequester(),
                "PURCHASE_QUOTED",
                purchaseRequest.getId(),
                quoter.getDisplayName() + " 已對「" + purchaseRequest.getItemName() + "」提出報價"
        );
        return toQuoteResponse(quote);
    }

    @Transactional(readOnly = true)
    public List<PurchaseQuoteResponse> getRequestQuotes(Long userId, Long requestId) {
        PurchaseRequest purchaseRequest = findRequest(requestId);
        if (!purchaseRequest.getRequester().getId().equals(userId)) {
            throw new RuntimeException("只有委託人可以查看完整報價列表");
        }
        return quoteRepository.findByPurchaseRequestIdOrderByCreatedAtDesc(requestId)
                .stream()
                .map(this::toQuoteResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PurchaseQuoteResponse> getMyQuotes(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new RuntimeException("分頁參數不合法，size 必須介於 1 到 100");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return quoteRepository.findByQuoterIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toQuoteResponse);
    }

    @Transactional
    public PurchaseQuoteResponse updateQuote(Long userId, Long quoteId, PurchaseQuoteRequest request) {
        validateQuotePayload(request);
        PurchaseQuote quoteReference = findQuote(quoteId);
        PurchaseRequest purchaseRequest = lockRequest(quoteReference.getPurchaseRequest().getId());
        PurchaseQuote quote = lockQuote(quoteId);
        validateQuoteOwner(userId, quote);
        validateActiveQuote(quote);
        validateQuoteableRequest(purchaseRequest);

        quote.setQuoteAmount(request.getQuoteAmount());
        quote.setQuoteNote(request.getQuoteNote());
        return toQuoteResponse(quoteRepository.save(quote));
    }

    @Transactional
    public PurchaseQuoteResponse withdrawQuote(Long userId, Long quoteId) {
        PurchaseQuote quoteReference = findQuote(quoteId);
        PurchaseRequest purchaseRequest = lockRequest(quoteReference.getPurchaseRequest().getId());
        PurchaseQuote quote = lockQuote(quoteId);
        validateQuoteOwner(userId, quote);
        validateActiveQuote(quote);
        if ("ORDERED".equals(purchaseRequest.getStatus())) {
            throw new RuntimeException("委託單已成立訂單，無法撤回報價");
        }

        quote.setStatus("WITHDRAWN");
        quote.setWithdrawnAt(LocalDateTime.now());
        quoteRepository.save(quote);

        // 最後一筆有效報價撤回後，委託單恢復為可報價的 OPEN 狀態。
        if ("QUOTING".equals(purchaseRequest.getStatus())
                && !quoteRepository.existsByPurchaseRequestIdAndStatus(purchaseRequest.getId(), ACTIVE)) {
            purchaseRequest.setStatus("OPEN");
            purchaseRequestRepository.save(purchaseRequest);
        }
        return toQuoteResponse(quote);
    }

    @Transactional
    public PurchaseOrderResponse selectQuote(Long userId, Long quoteId) {
        PurchaseQuote quoteReference = findQuote(quoteId);
        PurchaseRequest purchaseRequest = lockRequest(quoteReference.getPurchaseRequest().getId());
        PurchaseQuote quote = lockQuote(quoteId);
        if (!purchaseRequest.getRequester().getId().equals(userId)) {
            throw new RuntimeException("只有委託人可以選定報價");
        }
        if (!"QUOTE".equals(purchaseRequest.getRewardType())) {
            throw new RuntimeException("固定酬金委託不可選定報價");
        }
        validateQuoteableRequest(purchaseRequest);
        validateActiveQuote(quote);
        validateCandidate(purchaseRequest, quote.getQuoter());

        // 先保留未得標報價者，批次更新狀態後仍可逐一發送通知。
        List<PurchaseQuote> notSelectedQuotes = quoteRepository
                .findByPurchaseRequestIdAndStatusAndIdNot(purchaseRequest.getId(), ACTIVE, quoteId);

        PurchaseOrder order = createOrder(purchaseRequest, quote.getQuoter(), quote);
        purchaseChatService.createForOrder(order);
        purchaseRequest.setStatus("ORDERED");
        quote.setStatus("SELECTED");
        quote.setSelectedAt(LocalDateTime.now());
        purchaseRequestRepository.save(purchaseRequest);
        quoteRepository.save(quote);
        quoteRepository.markOtherActiveQuotesNotSelected(purchaseRequest.getId(), quoteId);
        recordOrderEvent(order, purchaseRequest.getRequester(), "ORDER_CREATED_FROM_QUOTE");

        notificationService.sendNotification(
                quote.getQuoter(),
                "PURCHASE_QUOTE_SELECTED",
                order.getId(),
                "您對「" + purchaseRequest.getItemName() + "」的報價已被選中"
        );
        for (PurchaseQuote notSelected : notSelectedQuotes) {
            notificationService.sendNotification(
                    notSelected.getQuoter(),
                    "PURCHASE_QUOTE_NOT_SELECTED",
                    purchaseRequest.getId(),
                    "您對「" + purchaseRequest.getItemName() + "」的報價未被選中"
            );
        }
        return toOrderResponse(order);
    }

    @Transactional
    public PurchaseOrderResponse acceptFixedRequest(Long userId, Long requestId) {
        PurchaseRequest purchaseRequest = lockRequest(requestId);
        if (!"FIXED".equals(purchaseRequest.getRewardType())) {
            throw new RuntimeException("只有固定酬金委託可以直接承接");
        }
        if (!"OPEN".equals(purchaseRequest.getStatus())) {
            throw new RuntimeException("目前委託單狀態不可承接");
        }
        validateNotExpired(purchaseRequest);
        User runner = findUser(userId);
        validateCandidate(purchaseRequest, runner);

        PurchaseOrder order = createOrder(purchaseRequest, runner, null);
        purchaseChatService.createForOrder(order);
        purchaseRequest.setStatus("ORDERED");
        purchaseRequestRepository.save(purchaseRequest);
        recordOrderEvent(order, runner, "ORDER_CREATED_BY_ACCEPT");

        notificationService.sendNotification(
                purchaseRequest.getRequester(),
                "PURCHASE_ACCEPTED",
                order.getId(),
                runner.getDisplayName() + " 已承接您的託購「" + purchaseRequest.getItemName() + "」"
        );
        notificationService.sendNotification(
                runner,
                "PURCHASE_ACCEPTED",
                order.getId(),
                "您已承接託購「" + purchaseRequest.getItemName() + "」"
        );
        return toOrderResponse(order);
    }

    private PurchaseOrder createOrder(
            PurchaseRequest purchaseRequest,
            User runner,
            PurchaseQuote selectedQuote
    ) {
        PurchaseOrder order = PurchaseOrder.builder()
                .purchaseRequest(purchaseRequest)
                .requester(purchaseRequest.getRequester())
                .runner(runner)
                .selectedQuote(selectedQuote)
                .status("WAITING_CONFIRMATION")
                .build();
        return orderRepository.save(order);
    }

    private void recordOrderEvent(PurchaseOrder order, User actor, String eventType) {
        PurchaseOrderEvent event = PurchaseOrderEvent.builder()
                .purchaseOrder(order)
                .actor(actor)
                .eventType(eventType)
                .fromStatus(null)
                .toStatus("WAITING_CONFIRMATION")
                .description("託購訂單已成立")
                .build();
        orderEventRepository.save(event);
    }

    private void validateQuoteableRequest(PurchaseRequest purchaseRequest) {
        if (!"QUOTE".equals(purchaseRequest.getRewardType())) {
            throw new RuntimeException("只有報價制委託可以提出或修改報價");
        }
        if (!QUOTEABLE_REQUEST_STATUSES.contains(purchaseRequest.getStatus())) {
            throw new RuntimeException("目前委託單狀態不可報價");
        }
        validateNotExpired(purchaseRequest);
    }

    private void validateQuotePayload(PurchaseQuoteRequest request) {
        if (request == null || request.getQuoteAmount() == null || request.getQuoteAmount() <= 0) {
            throw new RuntimeException("報價金額必須大於 0");
        }
        if (request.getQuoteNote() != null && request.getQuoteNote().length() > 500) {
            throw new RuntimeException("報價備註不可超過 500 字");
        }
    }

    private void validateNotExpired(PurchaseRequest purchaseRequest) {
        if (!purchaseRequest.getRequestExpireTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("委託期限已過");
        }
    }

    private void validateCandidate(PurchaseRequest purchaseRequest, User candidate) {
        User requester = purchaseRequest.getRequester();
        if (requester.getId().equals(candidate.getId())) {
            throw new RuntimeException("委託人不可承接或報價自己的委託單");
        }
        int creditScore = candidate.getCreditScore() == null ? 0 : candidate.getCreditScore();
        int minimumScore = purchaseRequest.getMinCreditScore() == null ? 0 : purchaseRequest.getMinCreditScore();
        if (creditScore < minimumScore) {
            throw new RuntimeException("信用分未達委託單要求");
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(requester.getId(), candidate.getId())
                || blockRepository.existsByBlockerIdAndBlockedId(candidate.getId(), requester.getId())) {
            throw new RuntimeException("雙方存在封鎖關係，無法成立交易");
        }
    }

    private void validateQuoteOwner(Long userId, PurchaseQuote quote) {
        if (!quote.getQuoter().getId().equals(userId)) {
            throw new RuntimeException("只有報價者本人可以操作此報價");
        }
    }

    private void validateActiveQuote(PurchaseQuote quote) {
        if (!ACTIVE.equals(quote.getStatus())) {
            throw new RuntimeException("只有有效報價可以執行此操作");
        }
    }

    private PurchaseRequest lockRequest(Long requestId) {
        return purchaseRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new RuntimeException("找不到委託單"));
    }

    private PurchaseQuote lockQuote(Long quoteId) {
        return quoteRepository.findByIdForUpdate(quoteId)
                .orElseThrow(() -> new RuntimeException("找不到報價"));
    }

    private PurchaseQuote findQuote(Long quoteId) {
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("找不到報價"));
    }

    private PurchaseRequest findRequest(Long requestId) {
        return purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("找不到委託單"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("找不到目前登入使用者"));
    }

    private PurchaseQuoteResponse toQuoteResponse(PurchaseQuote quote) {
        return PurchaseQuoteResponse.builder()
                .id(quote.getId())
                .purchaseRequestId(quote.getPurchaseRequest().getId())
                .itemName(quote.getPurchaseRequest().getItemName())
                .quoterId(quote.getQuoter().getId())
                .quoterDisplayName(quote.getQuoter().getDisplayName())
                .quoterProfileImageUrl(quote.getQuoter().getProfileImageUrl())
                .quoterCreditScore(quote.getQuoter().getCreditScore())
                .quoteAmount(quote.getQuoteAmount())
                .quoteNote(quote.getQuoteNote())
                .status(quote.getStatus())
                .selectedAt(quote.getSelectedAt())
                .withdrawnAt(quote.getWithdrawnAt())
                .createdAt(quote.getCreatedAt())
                .updatedAt(quote.getUpdatedAt())
                .build();
    }

    private PurchaseOrderResponse toOrderResponse(PurchaseOrder order) {
        return PurchaseOrderResponse.builder()
                .id(order.getId())
                .purchaseRequestId(order.getPurchaseRequest().getId())
                .itemName(order.getPurchaseRequest().getItemName())
                .requesterId(order.getRequester().getId())
                .requesterDisplayName(order.getRequester().getDisplayName())
                .runnerId(order.getRunner().getId())
                .runnerDisplayName(order.getRunner().getDisplayName())
                .selectedQuoteId(order.getSelectedQuote() == null ? null : order.getSelectedQuote().getId())
                .chatRoomId(purchaseChatService.findRoomId(order.getId()))
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
