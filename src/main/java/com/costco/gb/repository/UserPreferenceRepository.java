package com.costco.gb.repository;

import com.costco.gb.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    // 主鍵就是 user_id，所以直接用內建的 findById() 即可
}