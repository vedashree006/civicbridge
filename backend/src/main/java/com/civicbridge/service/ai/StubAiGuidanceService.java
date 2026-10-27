package com.civicbridge.service.ai;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default no-op-ish implementation so the API is usable end-to-end before an
 * LLM provider is wired up. Replace with a real client (e.g. calling the
 * Claude API) behind the same interface — controllers/services never change.
 */
@Service
public class StubAiGuidanceService implements AiGuidanceService {

    @Override
    public List<String> reviewDocument(String category, String extractedText) {
        return List.of(
                "AI guidance is not yet configured for this environment.",
                "Once enabled, this will check your '" + category + "' documents for missing fields, " +
                        "unclear scans, and mismatched information before an officer reviews them."
        );
    }
}
