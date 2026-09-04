package com.costco.gb.repository;

import com.costco.gb.entity.InquiryAnswer;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InquiryAnswerRepository extends JpaRepository<InquiryAnswer, Long> {

    List<InquiryAnswer> findByInquiryId(Long inquiryId, Sort sort);

    long countByInquiryId(Long inquiryId);

    boolean existsByInquiryId(Long inquiryId);

    Optional<InquiryAnswer> findByIdAndInquiryId(Long id, Long inquiryId);
}
