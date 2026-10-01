package com.civicbridge.service;

import com.civicbridge.dto.CaseCreateRequest;
import com.civicbridge.dto.CaseResponse;
import com.civicbridge.dto.CaseStatusUpdateRequest;
import com.civicbridge.entity.CaseEntity;
import com.civicbridge.entity.CaseStatus;
import com.civicbridge.entity.Role;
import com.civicbridge.entity.User;
import com.civicbridge.exception.AccessDeniedCustomException;
import com.civicbridge.exception.ResourceNotFoundException;
import com.civicbridge.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Business rules for case lifecycle. Kept storage/notification/audit concerns
 * behind their own services (SRP) so this class only orchestrates the use case.
 */
@Service
@RequiredArgsConstructor
public class CaseService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CaseRepository caseRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public CaseResponse createCase(CaseCreateRequest request, User citizen) {
        CaseEntity caseEntity = CaseEntity.builder()
                .referenceNo(generateReferenceNo())
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .status(CaseStatus.SUBMITTED)
                .citizen(citizen)
                .build();

        caseRepository.save(caseEntity);
        auditService.log(citizen.getId(), "CASE_CREATED", "Case", caseEntity.getId(), caseEntity.getReferenceNo());
        notificationService.notifyUser(citizen, "Your case " + caseEntity.getReferenceNo() + " was submitted.");

        return CaseResponse.from(caseEntity);
    }

    public CaseResponse getCaseForUser(Long caseId, User requester) {
        CaseEntity caseEntity = getOrThrow(caseId);
        assertCanView(caseEntity, requester);
        return CaseResponse.from(caseEntity);
    }

    public Page<CaseResponse> listCasesForUser(User requester, Pageable pageable) {
        Page<CaseEntity> page = switch (requester.getRole()) {
            case CITIZEN -> caseRepository.findByCitizenId(requester.getId(), pageable);
            case OFFICER -> caseRepository.findByAssignedOfficerId(requester.getId(), pageable);
            case ADMIN -> caseRepository.findAll(pageable);
        };
        return page.map(CaseResponse::from);
    }

    public CaseResponse assignOfficer(Long caseId, User officer, User actor) {
        assertStaff(actor);
        CaseEntity caseEntity = getOrThrow(caseId);
        caseEntity.setAssignedOfficer(officer);
        caseRepository.save(caseEntity);
        auditService.log(actor.getId(), "CASE_ASSIGNED", "Case", caseId, "officer=" + officer.getEmail());
        return CaseResponse.from(caseEntity);
    }

    public CaseResponse updateStatus(Long caseId, CaseStatusUpdateRequest request, User actor) {
        assertStaff(actor);
        CaseEntity caseEntity = getOrThrow(caseId);
        caseEntity.setStatus(request.status());
        caseRepository.save(caseEntity);

        auditService.log(actor.getId(), "CASE_STATUS_CHANGED", "Case", caseId,
                "newStatus=" + request.status() + " comment=" + request.comment());
        notificationService.notifyUser(caseEntity.getCitizen(),
                "Your case " + caseEntity.getReferenceNo() + " is now " + request.status());

        return CaseResponse.from(caseEntity);
    }

    private void assertCanView(CaseEntity caseEntity, User requester) {
        boolean isOwner = caseEntity.getCitizen().getId().equals(requester.getId());
        boolean isAssignedOfficer = caseEntity.getAssignedOfficer() != null
                && caseEntity.getAssignedOfficer().getId().equals(requester.getId());
        boolean isStaff = requester.getRole() == Role.ADMIN || requester.getRole() == Role.OFFICER;

        if (!(isOwner || isAssignedOfficer || isStaff)) {
            throw new AccessDeniedCustomException("You do not have access to this case");
        }
    }

    private void assertStaff(User user) {
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.OFFICER) {
            throw new AccessDeniedCustomException("Only officers or admins may perform this action");
        }
    }

    private CaseEntity getOrThrow(Long id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + id));
    }

    private String generateReferenceNo() {
        int suffix = 100000 + RANDOM.nextInt(900000);
        return "CB-" + java.time.Year.now().getValue() + "-" + suffix;
    }
}
