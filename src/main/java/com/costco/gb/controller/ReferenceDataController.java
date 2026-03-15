package com.costco.gb.controller;

import com.costco.gb.dto.response.CategoryResponse;
import com.costco.gb.dto.response.StoreResponse;
import com.costco.gb.service.ReferenceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    // 取得所有營業中的門市
    @GetMapping("/stores")
    public ResponseEntity<List<StoreResponse>> getStores() {
        return ResponseEntity.ok(referenceDataService.getActiveStores());
    }

    // 取得所有商品分類
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(referenceDataService.getAllCategories());
    }
}