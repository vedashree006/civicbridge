package com.civicbridge.controller;

import com.civicbridge.analytics.AnalyticsService;
import com.civicbridge.analytics.EventType;
import com.civicbridge.dto.CaseCreateRequest;
import com.civicbridge.dto.CaseResponse;
import com.civicbridge.dto.CaseStatusUpdateRequest;
import com.civicbridge.entity.Role;
import com.civicbridge.entity.User;
import com.civicbridge.exception.ResourceNotFoundException;
import com.civicbridge.repository.UserRepository;
import com.civicbridge.service.CaseService;
import com.civicbridge.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    @PostMapping
    public ResponseEntity<CaseResponse> create(@Valid @RequestBody CaseCreateRequest request) {
        User citizen = currentUserService.getCurrentUser();
        CaseResponse created = caseService.createCase(request, citizen);
        analyticsService.recordForCurrentRequest(EventType.CASE_SUBMITTED, citizen.getId());
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<Page<CaseResponse>> list(Pageable pageable) {
        User requester = currentUserService.getCurrentUser();
        return ResponseEntity.ok(caseService.listCasesForUser(requester, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseResponse> get(@PathVariable Long id) {
        User requester = currentUserService.getCurrentUser();
        return ResponseEntity.ok(caseService.getCaseForUser(id, requester));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CaseResponse> updateStatus(@PathVariable Long id,
                                                       @Valid @RequestBody CaseStatusUpdateRequest request) {
        User actor = currentUserService.getCurrentUser();
        return ResponseEntity.ok(caseService.updateStatus(id, request, actor));
    }

    @PatchMapping("/{id}/assign/{officerId}")
    public ResponseEntity<CaseResponse> assign(@PathVariable Long id, @PathVariable Long officerId) {
        User actor = currentUserService.getCurrentUser();
        User officer = userRepository.findById(officerId)
                .filter(u -> u.getRole() == Role.OFFICER)
                .orElseThrow(() -> new ResourceNotFoundException("Officer not found: " + officerId));
        return ResponseEntity.ok(caseService.assignOfficer(id, officer, actor));
    }
}
