package com.example.plms.credit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditAssessmentRepository extends JpaRepository<CreditAssessment, String> {

    List<CreditAssessment> findByApplicationIdOrderByAssessedAtDesc(String applicationId);

    Optional<CreditAssessment> findFirstByApplicationIdOrderByAssessedAtDesc(String applicationId);
}
