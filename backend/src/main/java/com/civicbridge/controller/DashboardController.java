package com.civicbridge.controller;

import com.civicbridge.dto.DashboardSummaryResponse;
import com.civicbridge.entity.CaseStatus;
import com.civicbridge.entity.User;
import com.civicbridge.repository.CaseRepository;
import com.civicbridge.service.CurrentUserService;
import com.civicbridge.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CaseRepository caseRepository;
    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary() {
        User user = currentUserService.getCurrentUser();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (CaseStatus status : CaseStatus.values()) {
            long count = caseRepository.countByStatus(status);
            byStatus.put(status.name(), count);
            total += count;
        }

        long unread = notificationService.unreadCount(user.getId());

        return ResponseEntity.ok(new DashboardSummaryResponse(total, byStatus, unread));
    }
}
