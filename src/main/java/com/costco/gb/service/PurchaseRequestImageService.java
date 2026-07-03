package com.costco.gb.service;

import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.repository.PurchaseQuoteRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
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

@Service
@RequiredArgsConstructor
public class PurchaseRequestImageService {
    private final PurchaseRequestRepository requestRepository;
    private final PurchaseQuoteRepository quoteRepository;

    @Transactional
    public List<String> upload(Long userId, Long requestId, List<MultipartFile> files) {
        PurchaseRequest request = editable(userId, requestId);
        if (files == null || files.isEmpty()) throw new RuntimeException("請選擇圖片");
        List<String> urls = new ArrayList<>(request.getImageUrls() == null ? List.of() : request.getImageUrls());
        if (urls.size() + files.size() > 5) throw new RuntimeException("託購圖片最多 5 張");
        Path dir = Paths.get("uploads", "purchase-requests", requestId.toString()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty() || file.getContentType() == null
                        || !file.getContentType().startsWith("image/")) {
                    throw new RuntimeException("只允許上傳圖片檔案");
                }
                String original = file.getOriginalFilename() == null ? "image.jpg" : file.getOriginalFilename();
                String extension = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".jpg";
                String filename = UUID.randomUUID() + extension;
                Thumbnails.of(file.getInputStream()).size(1280, 1280).outputQuality(0.8).toFile(dir.resolve(filename).toFile());
                urls.add("/uploads/purchase-requests/" + requestId + "/" + filename);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("託購圖片上傳失敗");
        }
        request.setImageUrls(urls);
        requestRepository.save(request);
        return urls;
    }

    @Transactional
    public List<String> delete(Long userId, Long requestId, String filename) {
        PurchaseRequest request = editable(userId, requestId);
        String safeName = Paths.get(filename).getFileName().toString();
        String url = "/uploads/purchase-requests/" + requestId + "/" + safeName;
        List<String> urls = new ArrayList<>(request.getImageUrls() == null ? List.of() : request.getImageUrls());
        if (!urls.remove(url)) throw new RuntimeException("找不到指定圖片");
        try {
            Path base = Paths.get("uploads", "purchase-requests", requestId.toString()).toAbsolutePath().normalize();
            Path target = base.resolve(safeName).normalize();
            if (!target.startsWith(base)) throw new RuntimeException("圖片路徑不合法");
            Files.deleteIfExists(target);
        } catch (Exception e) {
            throw new RuntimeException("刪除圖片失敗");
        }
        request.setImageUrls(urls);
        requestRepository.save(request);
        return urls;
    }

    @Transactional
    public List<String> reorder(Long userId, Long requestId, List<String> imageUrls) {
        PurchaseRequest request = editable(userId, requestId);
        List<String> current = request.getImageUrls() == null ? List.of() : request.getImageUrls();
        if (imageUrls == null || imageUrls.size() != current.size()
                || !new HashSet<>(imageUrls).equals(new HashSet<>(current))) {
            throw new RuntimeException("圖片排序清單與現有圖片不一致");
        }
        request.setImageUrls(new ArrayList<>(imageUrls));
        requestRepository.save(request);
        return request.getImageUrls();
    }

    private PurchaseRequest editable(Long userId, Long requestId) {
        PurchaseRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new RuntimeException("找不到委託單"));
        if (!request.getRequester().getId().equals(userId)) throw new RuntimeException("只有委託人可以管理圖片");
        if (!"OPEN".equals(request.getStatus())
                || quoteRepository.existsByPurchaseRequestIdAndStatus(requestId, "ACTIVE")) {
            throw new RuntimeException("已有報價或委託已成立後不可修改圖片");
        }
        return request;
    }
}
