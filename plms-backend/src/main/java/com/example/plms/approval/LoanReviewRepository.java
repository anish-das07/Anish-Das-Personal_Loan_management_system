package com.example.plms.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanReviewRepository extends JpaRepository<LoanReview, String> {

    List<LoanReview> findByApplicationIdOrderByCreatedAtAsc(String applicationId);
}
