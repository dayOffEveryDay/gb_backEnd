package com.costco.gb.controller;

import com.costco.gb.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    // 上傳多張聊天室圖片
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "campaignId", required = false) Long campaignId) {

        // 拿到的會是一個 List 裡面裝著多個網址
        List<String> imageUrls = fileService.uploadChatImages(files, campaignId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "urls", imageUrls
        ));
    }
}
