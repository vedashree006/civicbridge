package com.civicbridge.service.ai;

import java.util.List;

/**
 * Pluggable interface for AI-assisted document guidance (Phase 3).
 * Implementations analyze extracted document text against a case category's
 * requirements and return actionable feedback for the citizen.
 */
public interface AiGuidanceService {
    List<String> reviewDocument(String category, String extractedText);
}
