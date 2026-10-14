package com.htttdn.hrm.dto.request.employee;

import com.htttdn.hrm.entity.enums.EducationLevel;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * The part of their own record an employee keeps up to date: contact details and education. Name,
 * birth date, identity, tax and bank details feed contracts, insurance and payroll, so HR changes those.
 * Every field is replaced; null clears it.
 */
public record UpdateSelfProfileRequest(
    @Size(max = 20)
    @Nullable
    String phone,

    @Email
    @Size(max = 100)
    @Nullable
    String personalEmail,

    @Nullable
    String address,

    @Nullable
    EducationLevel highestEducationLevel,

    @Size(max = 200)
    @Nullable
    String major,

    @Size(max = 200)
    @Nullable
    String institution,

    @Nullable
    Short graduationYear
) {
}
