package com.costco.gb.service;

import com.costco.gb.logging.BusinessLog;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    private static final String BASE_UPLOAD_DIR = "uploads/campaigns/";
    private static final String INQUIRY_UPLOAD_DIR = "uploads/inquiries/";

    private final String baseUrl;

    public FileService(@Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    // 上傳聊天室圖片，回傳可由前端直接載入的完整後端網址。
    public List<String> uploadChatImages(List<MultipartFile> files, Long campaignId) {
        long startedAt = System.nanoTime();
        List<String> uploadedUrls = new ArrayList<>();

        try {
            String specificDir = campaignId != null ? BASE_UPLOAD_DIR + campaignId + "/" : "uploads/general/";
            Path uploadPath = Paths.get(specificDir);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 使用 UUID 產生檔名，避免原始檔名衝突或暴露使用者本機資訊。
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }

                String originalFilename = StringUtils.cleanPath(
                        file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.jpg"
                );
                String fileExtension = originalFilename.contains(".")
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";
                String newFilename = UUID.randomUUID() + fileExtension;
                Path filePath = uploadPath.resolve(newFilename);

                // 圖片先壓縮尺寸與品質，降低聊天室載入成本；非圖片則原樣保存。
                String contentType = file.getContentType();
                if (contentType != null && contentType.startsWith("image/")) {
                    Thumbnails.of(file.getInputStream())
                            .size(1280, 1280)
                            .outputQuality(0.75)
                            .toFile(filePath.toFile());
                } else {
                    Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                }

                String imageUrl = baseUrl + "/" + specificDir + newFilename;
                uploadedUrls.add(imageUrl);
                BusinessLog.warnOnRollback(log, "event=FILE_DB_RECONCILIATION_REQUIRED operation=CHAT_UPLOAD campaignId={} storageKey={}", campaignId, newFilename);
                log.debug("event=CHAT_FILE_WRITTEN campaignId={} storageKey={} bytes={}", campaignId, newFilename, file.getSize());
            }

            BusinessLog.info(log, "event=CHAT_FILES_UPLOADED campaignId={} count={} totalBytes={} durationMs={}", campaignId, uploadedUrls.size(), files.stream().filter(java.util.Objects::nonNull).mapToLong(MultipartFile::getSize).sum(), (System.nanoTime() - startedAt) / 1_000_000);
            return uploadedUrls;
        } catch (IOException e) {
            log.error("聊天室檔案上傳失敗，campaignId: {}", campaignId, e);
            throw new RuntimeException("檔案上傳失敗，請稍後再試");
        }
    }

    // 詢問附圖限制為三張有效圖片，避免非圖片檔案與過多檔案寫入公開目錄。
    public List<String> uploadInquiryImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        long startedAt = System.nanoTime();
        if (files.size() > 3) {
            throw new RuntimeException("問題附圖最多只能上傳 3 張");
        }

        try {
            Path uploadPath = Paths.get(INQUIRY_UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            List<String> uploadedUrls = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    throw new RuntimeException("問題附圖不可為空檔案");
                }
                String contentType = file.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    throw new RuntimeException("問題附圖僅支援圖片格式");
                }

                String originalFilename = StringUtils.cleanPath(
                        file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg"
                );
                String extension = originalFilename.contains(".")
                        ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
                String newFilename = UUID.randomUUID() + extension;
                Path filePath = uploadPath.resolve(newFilename);

                Thumbnails.of(file.getInputStream())
                        .size(1280, 1280)
                        .outputQuality(0.75)
                        .toFile(filePath.toFile());
                uploadedUrls.add(baseUrl + "/" + INQUIRY_UPLOAD_DIR + newFilename);
                BusinessLog.warnOnRollback(log, "event=FILE_DB_RECONCILIATION_REQUIRED operation=INQUIRY_UPLOAD storageKey={}", newFilename);
            }
            BusinessLog.info(log, "event=INQUIRY_IMAGES_UPLOADED count={} totalBytes={} durationMs={}", uploadedUrls.size(), files.stream().mapToLong(MultipartFile::getSize).sum(), (System.nanoTime() - startedAt) / 1_000_000);
            return uploadedUrls;
        } catch (IOException e) {
            log.error("問題附圖上傳失敗", e);
            throw new RuntimeException("問題附圖上傳失敗，請稍後再試");
        }
    }
}
