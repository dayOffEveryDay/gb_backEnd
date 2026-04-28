package com.costco.gb.repository;

import com.costco.gb.entity.RefreshToken;
import com.costco.gb.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    // 使用者登出時，把他的 Token 刪掉
    void deleteByUser(User user);
}