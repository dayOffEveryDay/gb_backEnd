package com.costco.gb.service;

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

    private final String baseUrl;

    public FileService(@Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    // 上傳聊天室圖片，回傳可由前端直接載入的完整後端網址。
    public List<String> uploadChatImages(List<MultipartFile> files, Long campaignId) {
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
                log.info("聊天室檔案已上傳: {}", imageUrl);
            }

            return uploadedUrls;
        } catch (IOException e) {
            log.error("聊天室檔案上傳失敗，campaignId: {}", campaignId, e);
            throw new RuntimeException("檔案上傳失敗，請稍後再試");
        }
    }
}
