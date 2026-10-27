package com.civicbridge.service;

import com.civicbridge.dto.DocumentResponse;
import com.civicbridge.entity.CaseEntity;
import com.civicbridge.entity.Document;
import com.civicbridge.entity.User;
import com.civicbridge.exception.ResourceNotFoundException;
import com.civicbridge.repository.CaseRepository;
import com.civicbridge.repository.DocumentRepository;
import com.civicbridge.service.storage.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CaseRepository caseRepository;
    private final DocumentStorageService storageService;
    private final AuditService auditService;

    public DocumentResponse upload(Long caseId, MultipartFile file, User uploader) throws IOException {
        CaseEntity caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + caseId));

        String storagePath = storageService.store(file, caseId);

        Document document = Document.builder()
                .caseEntity(caseEntity)
                .fileName(file.getOriginalFilename())
                .storagePath(storagePath)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .uploadedBy(uploader)
                .build();

        documentRepository.save(document);
        auditService.log(uploader.getId(), "DOCUMENT_UPLOADED", "Document", document.getId(), document.getFileName());

        return DocumentResponse.from(document);
    }

    public List<DocumentResponse> listForCase(Long caseId) {
        return documentRepository.findByCaseEntityId(caseId).stream()
                .map(DocumentResponse::from)
                .toList();
    }
}
