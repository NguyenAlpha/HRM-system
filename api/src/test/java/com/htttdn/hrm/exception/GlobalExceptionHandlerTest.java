package com.htttdn.hrm.exception;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void appExceptionPreservesStatusCodeMessageAndField() {
        var exception = new ConflictException(
            ErrorCode.EMPLOYEE_CODE_TAKEN,
            "Employee code already exists",
            "employeeCode"
        );

        var response = handler.handleAppException(exception);
        ApiResult<?> result = response.getBody();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(result);
        assertFalse(result.success());
        assertNull(result.data());
        assertEquals(ErrorCode.EMPLOYEE_CODE_TAKEN.name(), result.error().code());
        assertEquals("Employee code already exists", result.error().message());
        assertEquals("employeeCode", result.error().field());
    }

    @Test
    void resourceNotFoundReturnsNotFound() {
        var exception = new ResourceNotFoundException(
            ErrorCode.EMPLOYEE_NOT_FOUND,
            "Employee not found"
        );

        var response = handler.handleAppException(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.EMPLOYEE_NOT_FOUND.name(), response.getBody().error().code());
        assertNull(response.getBody().error().field());
    }

    @Test
    void optimisticLockReturnsConflict() {
        var response = handler.handleOptimisticLock(
            new OptimisticLockingFailureException("Concurrent update")
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.CONCURRENT_MODIFICATION.name(), response.getBody().error().code());
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalMessage() {
        var response = handler.handleUnexpected(new RuntimeException("Sensitive internal details"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INTERNAL_ERROR.name(), response.getBody().error().code());
        assertEquals("An unexpected error occurred", response.getBody().error().message());
    }
}
