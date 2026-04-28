package com.costco.gb.repository;

import com.costco.gb.entity.RefreshToken;
import com.costco.gb.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    // 登出或清除登入狀態時，刪除該使用者全部 Refresh Token
    void deleteByUser(User user);
}
