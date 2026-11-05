package com.example.plms.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentVerificationRepository extends JpaRepository<DocumentVerification, String> {

    List<DocumentVerification> findByDocumentIdOrderByDecidedAtAsc(String documentId);
}
