package com.civicbridge.dto;

import com.civicbridge.entity.CaseStatus;
import jakarta.validation.constraints.NotNull;

public record CaseStatusUpdateRequest(
        @NotNull CaseStatus status,
        String comment
) {}
