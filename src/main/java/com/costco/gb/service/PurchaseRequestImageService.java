package com.costco.gb.service;

import com.costco.gb.logging.BusinessLog;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.repository.PurchaseQuoteRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRequestImageService {
    private final PurchaseRequestRepository requestRepository;
    private final PurchaseQuoteRepository quoteRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public List<String> upload(Long userId, Long requestId, List<MultipartFile> files) {
        PurchaseRequest request = editable(userId, requestId);
        if (files == null || files.isEmpty()) {
            throw new RuntimeException("請至少上傳一張圖片");
        }

        List<String> imageKeys = toStorageImageUrls(request.getImageUrls());
        if (imageKeys.size() + files.size() > 5) {
            throw new RuntimeException("託購圖片最多 5 張");
        }

        int originalCount = imageKeys.size();
        long startedAt = System.nanoTime();
        Path dir = Paths.get("uploads", "purchase-requests", requestId.toString()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty() || file.getContentType() == null
                        || !file.getContentType().startsWith("image/")) {
                    throw new RuntimeException("只允許上傳圖片檔案");
                }

                // 與團購一致：檔案以 UUID 命名，DB 只保存 uploads 目錄下的相對 key。
                String original = file.getOriginalFilename() == null ? "image.jpg" : file.getOriginalFilename();
                String extension = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".jpg";
                String filename = UUID.randomUUID() + extension;
                Thumbnails.of(file.getInputStream())
                        .size(1280, 1280)
                        .outputQuality(0.8)
                        .toFile(dir.resolve(filename).toFile());
                imageKeys.add("purchase-requests/" + requestId + "/" + filename);
                BusinessLog.warnOnRollback(log, "event=FILE_DB_RECONCILIATION_REQUIRED operation=UPLOAD actorId={} requestId={} storageKey={} reasonCode=TRANSACTION_NOT_COMMITTED", userId, requestId, "purchase-requests/" + requestId + "/" + filename);
            }
        } catch (RuntimeException e) {
            log.warn("event=PURCHASE_IMAGE_UPLOAD_REJECTED actorId={} requestId={} requestedCount={} completedCount={} errorType={}", userId, requestId, files.size(), imageKeys.size() - originalCount, e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            log.error("event=PURCHASE_IMAGE_UPLOAD_FAILED actorId={} requestId={} requestedCount={} completedCount={} errorType={}", userId, requestId, files.size(), imageKeys.size() - originalCount, e.getClass().getSimpleName());
            throw new RuntimeException("託購圖片上傳失敗", e);
        }

        request.setImageUrls(imageKeys);
        requestRepository.save(request);
        BusinessLog.info(log, "event=PURCHASE_IMAGES_UPLOADED actorId={} requestId={} count={} totalBytes={} durationMs={}", userId, requestId, files.size(), files.stream().mapToLong(MultipartFile::getSize).sum(), (System.nanoTime() - startedAt) / 1_000_000);
        return toPublicImageUrls(imageKeys);
    }

    @Transactional
    public List<String> delete(Long userId, Long requestId, String filename) {
        PurchaseRequest request = editable(userId, requestId);
        String safeName = Paths.get(filename).getFileName().toString();
        String imageKey = "purchase-requests/" + requestId + "/" + safeName;
        List<String> imageKeys = toStorageImageUrls(request.getImageUrls());
        if (!imageKeys.remove(imageKey)) {
            throw new RuntimeException("找不到指定圖片");
        }

        try {
            // 刪檔前確認目標仍在該託購圖片目錄內，避免路徑穿越。
            Path base = Paths.get("uploads", "purchase-requests", requestId.toString()).toAbsolutePath().normalize();
            Path target = base.resolve(safeName).normalize();
            if (!target.startsWith(base)) {
                throw new RuntimeException("圖片路徑不合法");
            }
            boolean deleted = Files.deleteIfExists(target);
            log.debug("event=PURCHASE_IMAGE_FILE_DELETE actorId={} requestId={} fileExisted={}", userId, requestId, deleted);
            if (deleted) {
                BusinessLog.warnOnRollback(log, "event=FILE_DB_RECONCILIATION_REQUIRED operation=DELETE actorId={} requestId={} storageKey={} reasonCode=TRANSACTION_NOT_COMMITTED", userId, requestId, imageKey);
            }
        } catch (Exception e) {
            log.error("event=PURCHASE_IMAGE_DELETE_FAILED actorId={} requestId={} errorType={}", userId, requestId, e.getClass().getSimpleName());
            throw new RuntimeException("刪除圖片失敗", e);
        }

        request.setImageUrls(imageKeys);
        requestRepository.save(request);
        BusinessLog.info(log, "event=PURCHASE_IMAGE_DELETED actorId={} requestId={} remainingCount={}", userId, requestId, imageKeys.size());
        return toPublicImageUrls(imageKeys);
    }

    @Transactional
    public List<String> reorder(Long userId, Long requestId, List<String> imageUrls) {
        PurchaseRequest request = editable(userId, requestId);
        List<String> current = toStorageImageUrls(request.getImageUrls());
        List<String> requested = toStorageImageUrls(imageUrls);
        if (requested.size() != current.size() || !new HashSet<>(requested).equals(new HashSet<>(current))) {
            throw new RuntimeException("圖片排序內容必須與目前圖片一致");
        }

        request.setImageUrls(new ArrayList<>(requested));
        requestRepository.save(request);
        BusinessLog.info(log, "event=PURCHASE_IMAGES_REORDERED actorId={} requestId={} count={}", userId, requestId, requested.size());
        return toPublicImageUrls(request.getImageUrls());
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

    private PurchaseRequest editable(Long userId, Long requestId) {
        PurchaseRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new RuntimeException("找不到託購需求"));
        if (!request.getRequester().getId().equals(userId)) {
            throw new RuntimeException("只有託購發起人可以管理圖片");
        }
        if (!"OPEN".equals(request.getStatus())
                || quoteRepository.existsByPurchaseRequestIdAndStatus(requestId, "ACTIVE")) {
            throw new RuntimeException("已有報價或狀態不可編輯，無法管理圖片");
        }
        return request;
    }
}
