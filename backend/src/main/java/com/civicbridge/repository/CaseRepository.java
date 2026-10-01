package com.civicbridge.repository;

import com.civicbridge.entity.CaseEntity;
import com.civicbridge.entity.CaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseEntity, Long> {
    Optional<CaseEntity> findByReferenceNo(String referenceNo);
    Page<CaseEntity> findByCitizenId(Long citizenId, Pageable pageable);
    Page<CaseEntity> findByAssignedOfficerId(Long officerId, Pageable pageable);
    Page<CaseEntity> findByStatus(CaseStatus status, Pageable pageable);
    long countByStatus(CaseStatus status);
}
