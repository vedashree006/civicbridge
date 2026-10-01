package com.civicbridge.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Storage abstraction (DIP): swap LocalDiskStorageService for an S3-backed
 * implementation in Phase 3 without touching DocumentService or controllers.
 */
public interface DocumentStorageService {
    String store(MultipartFile file, Long caseId) throws IOException;
    byte[] retrieve(String storagePath) throws IOException;
}
