package com.civicbridge.service;

import com.civicbridge.dto.CaseCreateRequest;
import com.civicbridge.dto.CaseResponse;
import com.civicbridge.entity.CaseEntity;
import com.civicbridge.entity.Role;
import com.civicbridge.entity.User;
import com.civicbridge.repository.CaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock
    private CaseRepository caseRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CaseService caseService;

    private User citizen;

    @BeforeEach
    void setUp() {
        citizen = User.builder().id(1L).email("citizen@example.com").role(Role.CITIZEN).build();
    }

    @Test
    void createCase_savesEntityAndReturnsSubmittedStatus() {
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> {
            CaseEntity e = inv.getArgument(0);
            e.setId(42L);
            return e;
        });

        CaseCreateRequest request = new CaseCreateRequest("Broken streetlight", "Pole 12 on Main St", "public-works");
        CaseResponse response = caseService.createCase(request, citizen);

        assertThat(response.title()).isEqualTo("Broken streetlight");
        assertThat(response.status()).isEqualTo("SUBMITTED");
        assertThat(response.referenceNo()).startsWith("CB-");
        verify(notificationService).notifyUser(eq(citizen), any());
        verify(auditService).log(eq(1L), eq("CASE_CREATED"), eq("Case"), eq(42L), any());
    }
}
