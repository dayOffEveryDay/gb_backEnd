package com.costco.gb.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails; // 🌟 引入壓縮套件
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

    private final String BASE_UPLOAD_DIR = "uploads/campaigns/";

    // 🌟 參數改成 List<MultipartFile>
    public List<String> uploadChatImages(List<MultipartFile> files, Long campaignId) {

        List<String> uploadedUrls = new ArrayList<>();

        try {
            String specificDir = (campaignId != null) ? BASE_UPLOAD_DIR + campaignId + "/" : "uploads/general/";
            Path uploadPath = Paths.get(specificDir);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 🌟 跑迴圈處理每一張圖片
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;

                String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.jpg");
                String fileExtension = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
                String newFilename = UUID.randomUUID().toString() + fileExtension;
                Path filePath = uploadPath.resolve(newFilename);

                // 🌟 核心壓縮邏輯：判斷如果是圖片，就進行壓縮
                String contentType = file.getContentType();
                if (contentType != null && contentType.startsWith("image/")) {
                    // 使用 Thumbnailator 進行無腦壓縮
                    Thumbnails.of(file.getInputStream())
                            .size(1280, 1280)    // 限制最大長寬 (非常適合手機螢幕)
                            .outputQuality(0.75) // 畫質保留 75% (肉眼看不出差異，但檔案大小剩 1/5)
                            .toFile(filePath.toFile());
                } else {
                    // 如果前端傳了非圖片檔案(例如 PDF)，就直接原檔存入不壓縮
                    Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                }

                String imageUrl = "/" + specificDir + newFilename;
                uploadedUrls.add(imageUrl);
                log.info("聊天室檔案已上傳: {}", imageUrl);
            }

            return uploadedUrls; // 回傳包含多個網址的 List

        } catch (IOException e) {
            log.error("批量圖片儲存失敗，目標 ID: {}", campaignId, e);
            throw new RuntimeException("圖片上傳失敗，請稍後再試");
        }
    }
}