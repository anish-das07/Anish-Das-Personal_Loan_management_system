package com.example.plms.notification;

import com.example.plms.common.Actor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    public record NotificationResponse(String id, String template, String subject, String body, Instant createdAt,
                                       boolean read) {
    }

    @GetMapping
    public List<NotificationResponse> mine(Authentication auth) {
        return service.forUser(Actor.from(auth).userId()).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getTemplate(), n.getSubject(), n.getBody(),
                        n.getCreatedAt(), n.getReadAt() != null))
                .toList();
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable String id, Authentication auth) {
        service.markRead(Actor.from(auth).userId(), id);
        return ResponseEntity.noContent().build();
    }
}
