package com.costco.gb.repository;

import com.costco.gb.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InquiryRepository extends JpaRepository<Inquiry, Long>, JpaSpecificationExecutor<Inquiry> {

    Page<Inquiry> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<Inquiry> findByTypeAndStatusOrderByCreatedAtDesc(String type, String status, Pageable pageable);

    List<Inquiry> findByTypeAndStatusAndExpireTimeLessThanEqual(
            String type, String status, LocalDateTime expireTime);
}
