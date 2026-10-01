package com.civicbridge.dto;

import jakarta.validation.constraints.NotBlank;

public record CaseCreateRequest(
        @NotBlank String title,
        String description,
        @NotBlank String category
) {}
