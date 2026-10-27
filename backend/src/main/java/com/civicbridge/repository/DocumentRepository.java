package com.civicbridge.repository;

import com.civicbridge.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByCaseEntityId(Long caseId);
}
