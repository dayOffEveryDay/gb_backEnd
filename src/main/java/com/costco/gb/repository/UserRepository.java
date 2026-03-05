package com.costco.gb.repository;

import com.costco.gb.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // 登入時使用：透過 Line UID 尋找使用者
    Optional<User> findByLineUid(String lineUid);
}