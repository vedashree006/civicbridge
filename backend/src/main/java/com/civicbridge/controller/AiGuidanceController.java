package com.civicbridge.controller;

import com.civicbridge.analytics.AnalyticsService;
import com.civicbridge.analytics.EventType;
import com.civicbridge.service.CurrentUserService;
import com.civicbridge.service.ai.AiGuidanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiGuidanceController {

    private final AiGuidanceService aiGuidanceService;
    private final AnalyticsService analyticsService;
    private final CurrentUserService currentUserService;

    public record ReviewRequest(String category, String extractedText) {}

    @PostMapping("/review-document")
    public ResponseEntity<Map<String, List<String>>> review(@RequestBody ReviewRequest request) {
        List<String> feedback = aiGuidanceService.reviewDocument(request.category(), request.extractedText());
        analyticsService.recordForCurrentRequest(EventType.AI_GUIDANCE_USED, currentUserService.getCurrentUser().getId());
        return ResponseEntity.ok(Map.of("feedback", feedback));
    }
}
