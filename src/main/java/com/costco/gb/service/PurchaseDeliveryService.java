package com.costco.gb.service;

import com.costco.gb.logging.BusinessLog;
import com.costco.gb.dto.response.PurchaseRequestDeliveryDetailResponse;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.PurchaseRequestDeliveryDetail;
import com.costco.gb.entity.UserDeliveryProfile;
import com.costco.gb.repository.PurchaseRequestDeliveryDetailRepository;
import com.costco.gb.repository.UserDeliveryProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseDeliveryService {
    private final PurchaseRequestDeliveryDetailRepository detailRepository;
    private final UserDeliveryProfileRepository profileRepository;

    @Transactional
    public void replaceSnapshots(PurchaseRequest request, List<Long> selectedProfileIds) {
        if (selectedProfileIds == null || selectedProfileIds.isEmpty()
                || selectedProfileIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("請選擇至少一筆有效的常用交付資料");
        }
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(selectedProfileIds));
        Map<Long, UserDeliveryProfile> profiles = profileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserDeliveryProfile::getId, Function.identity()));
        if (profiles.size() != ids.size()) {
            throw new IllegalArgumentException("部分常用交付資料不存在");
        }

        // 全部驗證完成才取代快照；不可引用其他人的資料或混用交付方式。
        List<PurchaseRequestDeliveryDetail> snapshots = new ArrayList<>();
        for (Long id : ids) {
            UserDeliveryProfile profile = profiles.get(id);
            if (!profile.getUser().getId().equals(request.getRequester().getId())) {
                throw new IllegalArgumentException("不可使用其他使用者的常用交付資料");
            }
            if (!request.getDeliveryMethod().equals(profile.getProfileType())) {
                throw new IllegalArgumentException("常用交付資料類型必須與託購交付方式一致");
            }
            snapshots.add(PurchaseRequestDeliveryDetail.builder()
                    .purchaseRequest(request)
                    .sourceProfile(profile)
                    .city(profile.getCity())
                    .district(profile.getDistrict())
                    .locationName(profile.getLocationName())
                    .locationCode(profile.getLocationCode())
                    .provider(profile.getProvider())
                    .address(profile.getAddress())
                    .recipientName(profile.getRecipientName())
                    .recipientPhone(profile.getRecipientPhone())
                    .build());
        }
        detailRepository.deleteByPurchaseRequestId(request.getId());
        detailRepository.saveAll(snapshots);
        BusinessLog.info(log, "event=PURCHASE_DELIVERY_SNAPSHOTS_REPLACED actorId={} requestId={} profileIds={} deliveryMethod={} count={}", request.getRequester().getId(), request.getId(), ids, request.getDeliveryMethod(), snapshots.size());
    }

    @Transactional(readOnly = true)
    public List<PurchaseRequestDeliveryDetailResponse> read(PurchaseRequest request, boolean includePrivate) {
        boolean showLocation = includePrivate || "FACE_TO_FACE".equals(request.getDeliveryMethod());
        // 永遠從快照取值；公開資料只顯示區域及面交地點，不揭露收件資料或來源常用資料 ID。
        return detailRepository.findByPurchaseRequestIdOrderByIdAsc(request.getId()).stream()
                .map(detail -> PurchaseRequestDeliveryDetailResponse.builder()
                        .id(detail.getId())
                        .sourceProfileId(includePrivate && detail.getSourceProfile() != null
                                ? detail.getSourceProfile().getId() : null)
                        .city(detail.getCity())
                        .district(detail.getDistrict())
                        .locationName(showLocation ? detail.getLocationName() : null)
                        .locationCode(showLocation ? detail.getLocationCode() : null)
                        .provider(showLocation ? detail.getProvider() : null)
                        .address(includePrivate ? detail.getAddress() : null)
                        .recipientName(includePrivate ? detail.getRecipientName() : null)
                        .recipientPhone(includePrivate ? detail.getRecipientPhone() : null)
                        .build())
                .toList();
    }

    public String publicSummary(String deliveryMethod, List<PurchaseRequestDeliveryDetailResponse> details) {
        // 即使呼叫端持有完整快照，摘要也只能取區域與面交地點。
        String summary = details.stream().map(detail -> {
            String area = value(detail.getCity()) + value(detail.getDistrict());
            return "FACE_TO_FACE".equals(deliveryMethod)
                    ? area + (area.isEmpty() ? "" : " ") + value(detail.getLocationName())
                    : area;
        }).map(String::trim).filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining("、"));
        return summary.isEmpty() ? "交付地點請見詳情" : summary;
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
