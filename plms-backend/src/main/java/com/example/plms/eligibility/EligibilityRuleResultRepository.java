package com.example.plms.eligibility;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EligibilityRuleResultRepository extends JpaRepository<EligibilityRuleResult, String> {

    List<EligibilityRuleResult> findByAssessmentIdOrderBySeqAsc(String assessmentId);
}
