package com.costco.gb.repository;

import com.costco.gb.entity.InquiryFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface InquiryFollowRepository extends JpaRepository<InquiryFollow, Long> {

    boolean existsByInquiryIdAndUserId(Long inquiryId, Long userId);

    Optional<InquiryFollow> findByInquiryIdAndUserId(Long inquiryId, Long userId);

    long countByInquiryId(Long inquiryId);

    void deleteByInquiryIdAndUserId(Long inquiryId, Long userId);

    List<InquiryFollow> findByInquiryId(Long inquiryId);
}
