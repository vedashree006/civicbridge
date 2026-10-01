package com.civicbridge.dto;

import com.civicbridge.entity.Document;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        Long caseId,
        String fileName,
        String contentType,
        long sizeBytes,
        String uploadedByEmail,
        Instant uploadedAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(),
                d.getCaseEntity().getId(),
                d.getFileName(),
                d.getContentType(),
                d.getSizeBytes(),
                d.getUploadedBy().getEmail(),
                d.getUploadedAt()
        );
    }
}
