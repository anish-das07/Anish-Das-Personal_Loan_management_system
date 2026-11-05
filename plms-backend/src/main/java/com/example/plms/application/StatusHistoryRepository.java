package com.example.plms.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, String> {

    List<ApplicationStatusHistory> findByApplicationIdOrderByOccurredAtAsc(String applicationId);
}
