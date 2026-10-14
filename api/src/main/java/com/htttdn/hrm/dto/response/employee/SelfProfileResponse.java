package com.htttdn.hrm.dto.response.employee;

import com.htttdn.hrm.entity.enums.EducationLevel;

import jakarta.annotation.Nullable;

/** The fields an employee may change on their own record, with their current values. */
public record SelfProfileResponse(
    @Nullable String phone,
    @Nullable String personalEmail,
    @Nullable String address,
    @Nullable EducationLevel highestEducationLevel,
    @Nullable String major,
    @Nullable String institution,
    @Nullable Short graduationYear
) {
}
