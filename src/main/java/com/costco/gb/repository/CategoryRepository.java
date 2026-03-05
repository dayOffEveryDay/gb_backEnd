package com.costco.gb.repository;

import com.costco.gb.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {
    // 取得所有分類，並依照我們設定的權重排序
    List<Category> findAllByOrderBySortOrderAsc();
}