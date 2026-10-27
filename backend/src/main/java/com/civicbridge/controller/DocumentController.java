package com.civicbridge.controller;

import com.civicbridge.analytics.AnalyticsService;
import com.civicbridge.analytics.EventType;
import com.civicbridge.dto.DocumentResponse;
import com.civicbridge.entity.User;
import com.civicbridge.service.CurrentUserService;
import com.civicbridge.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/cases/{caseId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUserService currentUserService;
    private final AnalyticsService analyticsService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(@PathVariable Long caseId,
                                                     @RequestParam("file") MultipartFile file) throws IOException {
        User uploader = currentUserService.getCurrentUser();
        DocumentResponse saved = documentService.upload(caseId, file, uploader);
        analyticsService.recordForCurrentRequest(EventType.DOCUMENT_UPLOADED, uploader.getId());
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list(@PathVariable Long caseId) {
        return ResponseEntity.ok(documentService.listForCase(caseId));
    }
}
