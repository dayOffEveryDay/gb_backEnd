package com.costco.gb.service;

import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SharePageService {

    private final CampaignRepository campaignRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String backendBaseUrl;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.share.campaign-detail-path:/campaigns/{id}}")
    private String campaignDetailPath;

    @Value("${app.share.purchase-request-detail-path:/purchase-requests/{id}}")
    private String purchaseRequestDetailPath;

    @Transactional(readOnly = true)
    public String renderCampaignPage(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "團購不存在或已刪除"));

        // 已取消或進入取消流程的團購不再產生公開分享內容。
        if ("CANCELLED".equals(campaign.getStatus()) || "CANCEL_PENDING".equals(campaign.getStatus())) {
            throw new ResponseStatusException(HttpStatus.GONE, "團購已下架、不再公開");
        }

        // 分享頁只揭露原本就屬於公開資訊的欄位，避免將參團者或交易資訊帶入 LINE 預覽。
        String imageUrl = firstCampaignImageUrl(campaign.getImageUrls());
        String price = campaign.getPricePerUnit() == null
                ? "價格請見詳情"
                : "NT$ " + formatAmount(campaign.getPricePerUnit());
        String hostName = campaign.getHost().getDisplayName();
        String location = fallback(campaign.getMeetupLocation(), "地點請見詳情");
        String storeName = campaign.getStore().getName();
        String availableQuantity = campaign.getAvailableQuantity() == null
                ? "剩餘數量請見詳情"
                : "剩餘 " + campaign.getAvailableQuantity() + " 件";
        String description = storeName + "・" + price + "・" + availableQuantity;

        SharePageData data = new SharePageData(
                campaign.getItemName(),
                description,
                imageUrl,
                buildBackendUrl("/share/campaigns/" + campaignId),
                buildFrontendUrl(campaignDetailPath, campaignId),
                "團購商品",
                campaign.getItemName(),
                List.of(
                        new Detail("價位", price),
                        new Detail("主揪", hostName),
                        new Detail("面交地點", location),
                        new Detail("目前狀態", campaignStatusLabel(campaign.getStatus()))
                )
        );
        return render(data);
    }

    @Transactional(readOnly = true)
    public String renderPurchaseRequestPage(Long requestId) {
        PurchaseRequest request = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("找不到該託購需求"));

        // 託購分享頁刻意使用公開交付摘要，不讀取 deliveryPrivateDetail 內的地址與聯絡資料。
        String imageUrl = firstPurchaseRequestImageUrl(request.getImageUrls());
        String reward = rewardLabel(request);
        String requesterName = request.getRequester().getDisplayName();
        String location = fallback(request.getDeliveryPublicSummary(),
                fallback(request.getRequesterCity(), "交付地點請見詳情"));
        String description = reward + "・委託人 " + requesterName + "・" + location;

        SharePageData data = new SharePageData(
                request.getItemName() + "｜託購分享",
                description,
                imageUrl,
                buildBackendUrl("/share/purchase-requests/" + requestId),
                buildFrontendUrl(purchaseRequestDetailPath, requestId),
                "託購需求",
                request.getItemName(),
                List.of(
                        new Detail("酬勞", reward),
                        new Detail("委託人", requesterName),
                        new Detail("交付方式", location),
                        new Detail("目前狀態", purchaseRequestStatusLabel(request.getStatus()))
                )
        );
        return render(data);
    }

    private String render(SharePageData data) {
        String imageMeta = data.imageUrl() == null ? "" : """
                <meta property="og:image" content="%s">
                <meta name="twitter:image" content="%s">
                """.formatted(escape(data.imageUrl()), escape(data.imageUrl()));
        String image = data.imageUrl() == null
                ? "<div class=\"image-placeholder\">商品圖片請見詳情</div>"
                : "<img class=\"product-image\" src=\"%s\" alt=\"%s\">"
                .formatted(escape(data.imageUrl()), escape(data.itemName()));
        StringBuilder details = new StringBuilder();
        for (Detail detail : data.details()) {
            details.append("<div class=\"detail\"><span>")
                    .append(escape(detail.label()))
                    .append("</span><strong>")
                    .append(escape(detail.value()))
                    .append("</strong></div>");
        }

        // Open Graph 標籤放在初始 HTML 的 head，讓 LINE 爬蟲不需執行 JavaScript 即可取得預覽內容。
        return """
                <!doctype html>
                <html lang="zh-Hant">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>%s</title>
                  <meta name="description" content="%s">
                  <meta property="og:type" content="website">
                  <meta property="og:locale" content="zh_TW">
                  <meta property="og:site_name" content="Group Buy">
                  <meta property="og:title" content="%s">
                  <meta property="og:description" content="%s">
                  <meta property="og:url" content="%s">
                  %s
                  <meta name="twitter:card" content="summary_large_image">
                  <meta name="twitter:title" content="%s">
                  <meta name="twitter:description" content="%s">
                  <link rel="canonical" href="%s">
                  <style>
                    *{box-sizing:border-box}body{margin:0;background:#f4f5f7;color:#202124;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.wrap{max-width:680px;margin:0 auto;padding:24px 16px 48px}.card{overflow:hidden;background:#fff;border-radius:18px;box-shadow:0 8px 30px rgba(0,0,0,.08)}.product-image,.image-placeholder{display:block;width:100%%;aspect-ratio:1.91/1;object-fit:cover}.image-placeholder{display:grid;place-items:center;background:#e9ecef;color:#6c757d}.content{padding:22px}.tag{color:#087f5b;font-size:14px;font-weight:700}.item-name{margin:8px 0 18px;font-size:28px;line-height:1.3}.detail{display:flex;justify-content:space-between;gap:20px;padding:12px 0;border-top:1px solid #eee}.detail span{color:#70757a}.detail strong{text-align:right}.button{display:block;margin-top:22px;padding:14px 18px;border-radius:12px;background:#06c755;color:#fff;text-align:center;text-decoration:none;font-weight:700}
                  </style>
                </head>
                <body><main class="wrap"><article class="card">%s<div class="content"><div class="tag">%s</div><h1 class="item-name">%s</h1>%s<a class="button" href="%s">查看完整詳情</a></div></article></main></body>
                </html>
                """.formatted(
                escape(data.title()), escape(data.description()), escape(data.title()),
                escape(data.description()), escape(data.shareUrl()), imageMeta,
                escape(data.title()), escape(data.description()), escape(data.detailUrl()),
                image, escape(data.typeLabel()), escape(data.itemName()), details,
                escape(data.detailUrl())
        );
    }

    private String firstCampaignImageUrl(String imageUrls) {
        if (imageUrls == null || imageUrls.isBlank()) {
            return null;
        }
        return toPublicImageUrl(imageUrls.split(",")[0], "/images/");
    }

    private String firstPurchaseRequestImageUrl(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        return toPublicImageUrl(imageUrls.get(0), "/uploads/");
    }

    private String toPublicImageUrl(String value, String defaultPrefix) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("/uploads/") || trimmed.startsWith("/images/")) {
            return normalizedBackendBaseUrl() + trimmed;
        }
        if (trimmed.startsWith("uploads/") || trimmed.startsWith("images/")) {
            return normalizedBackendBaseUrl() + "/" + trimmed;
        }
        return normalizedBackendBaseUrl() + defaultPrefix + trimmed;
    }

    private String rewardLabel(PurchaseRequest request) {
        if ("FIXED".equalsIgnoreCase(request.getRewardType()) && request.getFixedRewardAmount() != null) {
            return "酬勞 NT$ " + formatAmount(request.getFixedRewardAmount());
        }
        return "酬勞由接單者報價";
    }

    private String buildBackendUrl(String path) {
        return normalizedBackendBaseUrl() + path;
    }

    private String formatAmount(Integer amount) {
        return String.format(Locale.TAIWAN, "%,d", amount);
    }

    private String buildFrontendUrl(String pathTemplate, Long id) {
        String normalizedBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.replaceAll("/+$", "");
        String path = pathTemplate.replace("{id}", id.toString());
        return normalizedBaseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private String normalizedBackendBaseUrl() {
        return backendBaseUrl == null ? "" : backendBaseUrl.replaceAll("/+$", "");
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String campaignStatusLabel(String status) {
        return switch (fallback(status, "")) {
            case "OPEN" -> "開放參加";
            case "FULL" -> "已滿團";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> fallback(status, "狀態請見詳情");
        };
    }

    private String purchaseRequestStatusLabel(String status) {
        return switch (fallback(status, "")) {
            case "OPEN" -> "開放接單";
            case "QUOTING" -> "報價中";
            case "ORDERED" -> "已接單";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            case "EXPIRED" -> "已過期";
            default -> fallback(status, "狀態請見詳情");
        };
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private record Detail(String label, String value) {
    }

    private record SharePageData(
            String title,
            String description,
            String imageUrl,
            String shareUrl,
            String detailUrl,
            String typeLabel,
            String itemName,
            List<Detail> details
    ) {
    }
}
