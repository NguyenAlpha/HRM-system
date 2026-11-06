package com.htttdn.hrm.exception;

import java.util.List;
import java.util.Set;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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
            ErrorCode.JOB_POSITION_CODE_TAKEN,
            "Job position code is already taken",
            "code"
        );

        var response = handler.handleAppException(exception);
        ApiResult<?> result = response.getBody();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(result);
        assertFalse(result.success());
        assertNull(result.data());
        assertEquals(ErrorCode.JOB_POSITION_CODE_TAKEN.name(), result.error().code());
        assertEquals("Job position code is already taken", result.error().message());
        assertEquals("code", result.error().field());
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
    void unknownRouteReturnsNotFoundInsteadOfInternalError() {
        var response = handler.handleNoRoute(new NoResourceFoundException(HttpMethod.GET, "/api/missing", "api/missing"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.name(), response.getBody().error().code());
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowedWithAllowHeader() {
        var response = handler.handleMethodNotAllowed(
            new HttpRequestMethodNotSupportedException("POST", Set.of("GET")));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(Set.of(HttpMethod.GET), response.getHeaders().getAllow());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.METHOD_NOT_ALLOWED.name(), response.getBody().error().code());
    }

    @Test
    void missingRequiredParameterReturnsValidationErrorWithItsName() {
        var response = handler.handleMissingParameter(new MissingServletRequestParameterException("year", "Integer"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.VALIDATION_ERROR.name(), response.getBody().error().code());
        assertEquals("year", response.getBody().error().field());
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() {
        var response = handler.handleUnsupportedMediaType(
            new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE.name(), response.getBody().error().code());
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
