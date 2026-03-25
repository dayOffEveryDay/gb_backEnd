package com.costco.gb.service;

import com.costco.gb.dto.response.CategoryResponse;
import com.costco.gb.dto.response.StoreResponse;
import com.costco.gb.repository.CategoryRepository;
import com.costco.gb.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReferenceDataService {

    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;

    public List<StoreResponse> getActiveStores() {
        return storeRepository.findByIsActiveTrue().stream()
                .map(store -> StoreResponse.builder()
                        .id(store.getId())
                        .name(store.getName())
                        .address(store.getAddress())
                        .openTime(store.getOpenTime().toString())
                        .closeTime(store.getCloseTime().toString())
                        .build())
                .collect(Collectors.toList());
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(category -> CategoryResponse.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .icon(category.getIcon())
                        .build())
                .collect(Collectors.toList());
    }
}