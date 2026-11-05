package com.example.plms.eligibility;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EligibilityPolicyRepository extends JpaRepository<EligibilityPolicyEntity, String> {

    List<EligibilityPolicyEntity> findAllByOrderByCreatedAtDesc();
}
