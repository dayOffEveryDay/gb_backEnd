package com.costco.gb.repository;

import com.costco.gb.entity.UserDeliveryProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDeliveryProfileRepository extends JpaRepository<UserDeliveryProfile, Long> {

    List<UserDeliveryProfile> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    List<UserDeliveryProfile> findByUserIdAndProfileTypeOrderByIsDefaultDescCreatedAtDesc(
            Long userId,
            String profileType
    );
}
