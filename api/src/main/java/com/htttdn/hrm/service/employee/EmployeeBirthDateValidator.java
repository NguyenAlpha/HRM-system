package com.htttdn.hrm.service.employee;

import java.time.LocalDate;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.BusinessException;

public final class EmployeeBirthDateValidator {

    private static final int MINIMUM_AGE = 17;

    private EmployeeBirthDateValidator() {
    }

    public static void validate(LocalDate birthDate, boolean required, LocalDate today) {
        if (birthDate == null) {
            if (required) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày sinh là bắt buộc", "dateOfBirth");
            }
            return;
        }
        if (birthDate.isAfter(today.minusYears(MINIMUM_AGE))) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Nhân sự phải đủ 17 tuổi",
                "dateOfBirth"
            );
        }
    }
}
