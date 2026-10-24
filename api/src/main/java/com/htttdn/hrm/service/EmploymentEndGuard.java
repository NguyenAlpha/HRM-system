package com.htttdn.hrm.service;

import java.time.LocalDate;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.BusinessException;

/**
 * Salary, insurance, dependents and assignments dated after an employee's last working day would never
 * apply, so they are refused. The last working day is set once employment ends or is scheduled to end;
 * records up to it can still be corrected, e.g. before the final payroll.
 */
final class EmploymentEndGuard {

    private EmploymentEndGuard() {
    }

    static void requireNotAfterEnd(Employee employee, LocalDate effectiveFrom, String field) {
        LocalDate lastDay = employee.getTerminationDate();
        if (lastDay != null && effectiveFrom.isAfter(lastDay)) {
            throw new BusinessException(ErrorCode.AFTER_EMPLOYMENT_END,
                field + " must not be after the last working day " + lastDay, field);
        }
    }

    /** An end date for a record of this employee: no later than the last working day, open only while still employed. */
    static LocalDate capAtEnd(Employee employee, LocalDate effectiveTo) {
        LocalDate lastDay = employee.getTerminationDate();
        return lastDay != null && (effectiveTo == null || effectiveTo.isAfter(lastDay)) ? lastDay : effectiveTo;
    }
}
