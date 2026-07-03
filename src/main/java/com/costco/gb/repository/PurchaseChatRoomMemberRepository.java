package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PurchaseChatRoomMemberRepository extends JpaRepository<PurchaseChatRoomMember, Long> {
    Optional<PurchaseChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
    List<PurchaseChatRoomMember> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
