package com.example.plms.eligibility;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EligibilityAssessmentRepository extends JpaRepository<EligibilityAssessment, String> {

    List<EligibilityAssessment> findByApplicationIdOrderByEvaluatedAtDesc(String applicationId);

    Optional<EligibilityAssessment> findFirstByApplicationIdOrderByEvaluatedAtDesc(String applicationId);
}
