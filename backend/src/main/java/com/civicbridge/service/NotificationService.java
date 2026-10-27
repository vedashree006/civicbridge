package com.civicbridge.service;

import com.civicbridge.entity.Notification;
import com.civicbridge.entity.User;
import com.civicbridge.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * MVP: writes an in-app notification row synchronously.
 * Phase 4 roadmap: publish a Kafka event instead and let a consumer fan out
 * to in-app + email/SMS without blocking the request thread.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void notifyUser(User user, String message) {
        Notification notification = Notification.builder()
                .user(user)
                .message(message)
                .build();
        notificationRepository.save(notification);
    }

    public Page<Notification> getForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }
}
