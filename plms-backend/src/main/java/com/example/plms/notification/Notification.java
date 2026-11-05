package com.example.plms.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Mock email/SMS delivery record. Bodies never contain secrets such as reset tokens. */
@Entity
@Table(name = "notification", indexes = @Index(name = "idx_notification_user", columnList = "user_id"))
public class Notification {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    @Column(nullable = false, length = 10)
    private String channel;
    @Column(nullable = false, length = 60)
    private String template;
    @Column(nullable = false, length = 160)
    private String subject;
    @Column(nullable = false, length = 1000)
    private String body;
    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(String userId, String channel, String template, String subject, String body,
                        Instant now) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.channel = channel;
        this.template = template;
        this.subject = subject;
        this.body = body;
        this.deliveryStatus = "SENT_MOCK";
        this.createdAt = now;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getChannel() {
        return channel;
    }

    public String getTemplate() {
        return template;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
