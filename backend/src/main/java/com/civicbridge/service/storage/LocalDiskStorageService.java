package com.civicbridge.service.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalDiskStorageService implements DocumentStorageService {

    private final Path uploadRoot;

    public LocalDiskStorageService(Path uploadRootPath) {
        this.uploadRoot = uploadRootPath;
    }

    @Override
    public String store(MultipartFile file, Long caseId) throws IOException {
        Path caseDir = uploadRoot.resolve(String.valueOf(caseId));
        Files.createDirectories(caseDir);

        String safeName = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
        Path target = caseDir.resolve(safeName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return target.toString();
    }

    @Override
    public byte[] retrieve(String storagePath) throws IOException {
        return Files.readAllBytes(Path.of(storagePath));
    }

    private String sanitize(String originalName) {
        if (originalName == null) return "file";
        return originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
