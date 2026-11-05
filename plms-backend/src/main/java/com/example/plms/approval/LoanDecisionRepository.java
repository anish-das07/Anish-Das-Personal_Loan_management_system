package com.example.plms.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanDecisionRepository extends JpaRepository<LoanDecision, String> {

    List<LoanDecision> findByApplicationIdOrderByDecidedAtAsc(String applicationId);

    long countByDecision(String decision);
}
