package com.example.plms.notification;

import com.example.plms.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Enqueues a mock notification (delivery is simulated). */
    @Transactional
    public void notify(String userId, String template, String subject, String body) {
        repository.save(new Notification(userId, "EMAIL", template, subject, body, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<Notification> forUser(String userId) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markRead(String userId, String notificationId) {
        Notification n = repository.findById(notificationId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.markRead(Instant.now(clock));
    }
}
