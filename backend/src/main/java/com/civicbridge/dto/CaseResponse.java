package com.civicbridge.dto;

import com.civicbridge.entity.CaseEntity;

import java.time.Instant;

public record CaseResponse(
        Long id,
        String referenceNo,
        String title,
        String description,
        String status,
        String category,
        String citizenEmail,
        String assignedOfficerEmail,
        Instant createdAt,
        Instant updatedAt
) {
    public static CaseResponse from(CaseEntity c) {
        return new CaseResponse(
                c.getId(),
                c.getReferenceNo(),
                c.getTitle(),
                c.getDescription(),
                c.getStatus().name(),
                c.getCategory(),
                c.getCitizen() != null ? c.getCitizen().getEmail() : null,
                c.getAssignedOfficer() != null ? c.getAssignedOfficer().getEmail() : null,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
