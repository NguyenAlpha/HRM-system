package com.htttdn.hrm.dto.request.employee;

import com.htttdn.hrm.entity.enums.EmploymentStatus;

/** Optional filters of the employee list; a null field means "no filter". */
public record EmployeeSearchCriteria(
    String keyword,
    EmploymentStatus employmentStatus,
    Long organizationUnitId,
    Long positionId
) {
}
