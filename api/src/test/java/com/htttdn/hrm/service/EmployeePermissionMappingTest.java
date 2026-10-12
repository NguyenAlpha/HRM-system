package com.htttdn.hrm.service;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.EmployeeSearchCriteria;
import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.EmploymentStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EmployeePermissionMappingTest {

    @Test
    void employeeUseCasesRequireAtomicPermissions() throws NoSuchMethodException {
        assertPermission(EmployeeService.class, "create", "employee.create", CreateEmployeeRequest.class);
        assertPermission(EmployeeService.class, "list", "employee.list.read", EmployeeSearchCriteria.class, Pageable.class);
        assertPermission(EmployeeService.class, "getById", "employee.read", Long.class);
        assertPermission(
            EmployeeService.class,
            "update",
            "employee.update",
            Long.class,
            UpdateEmployeeRequest.class
        );
        assertPermission(
            EmployeeService.class,
            "confirmEmployment",
            "employee.probation.confirm",
            Long.class
        );
        assertPermission(
            EmployeeService.class,
            "endEmployment",
            "employee.lifecycle.manage",
            Long.class,
            EmploymentStatus.class,
            LocalDate.class,
            String.class
        );
        assertPermission(
            EmployeeService.class,
            "softDelete",
            "employee.delete",
            Long.class,
            SoftDeleteEmployeeRequest.class
        );
    }

    @Test
    void assignmentUseCasesRequireAtomicPermissions() throws NoSuchMethodException {
        assertPermission(
            EmployeeAssignmentService.class,
            "createInitial",
            "employee.create",
            Employee.class,
            AssignEmployeeRequest.class
        );
        assertPermission(
            EmployeeAssignmentService.class,
            "assign",
            "employee.assignment.manage",
            Long.class,
            AssignEmployeeRequest.class
        );
        assertPermission(
            EmployeeAssignmentService.class,
            "list",
            "employee.assignment.read",
            Long.class
        );
        assertPermission(
            EmployeeAssignmentService.class,
            "getCurrent",
            "employee.assignment.read",
            Long.class
        );
    }

    private void assertPermission(
        Class<?> type,
        String methodName,
        String permission,
        Class<?>... parameterTypes
    ) throws NoSuchMethodException {
        PreAuthorize annotation = type.getMethod(methodName, parameterTypes).getAnnotation(PreAuthorize.class);

        assertNotNull(annotation, type.getSimpleName() + "." + methodName + " must declare @PreAuthorize");
        assertEquals("hasAuthority('" + permission + "')", annotation.value());
    }
}
