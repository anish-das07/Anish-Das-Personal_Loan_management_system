package com.example.plms.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

    List<Notification> findByUserIdAndTemplate(String userId, String template);

    long countByUserIdAndReadAtIsNull(String userId);
}
