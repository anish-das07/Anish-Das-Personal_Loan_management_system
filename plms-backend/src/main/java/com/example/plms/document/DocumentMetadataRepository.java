package com.example.plms.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, String> {

    List<DocumentMetadata> findByApplicationIdOrderByCategoryAscDocVersionDesc(String applicationId);

    Optional<DocumentMetadata> findFirstByApplicationIdAndCategoryOrderByDocVersionDesc(String applicationId,
                                                                                       String category);

    Page<DocumentMetadata> findByStatus(String status, Pageable pageable);
}
