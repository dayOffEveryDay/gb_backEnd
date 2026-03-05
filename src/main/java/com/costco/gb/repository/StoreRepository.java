package com.costco.gb.repository;

import com.costco.gb.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StoreRepository extends JpaRepository<Store, Integer> {
    // 取得所有有在營業的門市 (供前端下拉選單使用)
    List<Store> findByIsActiveTrue();
}