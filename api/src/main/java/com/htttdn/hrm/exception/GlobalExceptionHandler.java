package com.htttdn.hrm.exception;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.common.ErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResult<?>> handleAppException(AppException exception) {
        ErrorDetail error = exception.getField() == null
            ? ErrorDetail.of(exception.getErrorCode(), exception.getMessage())
            : ErrorDetail.of(exception.getErrorCode(), exception.getMessage(), exception.getField());

        return ResponseEntity.status(exception.getStatus()).body(ApiResult.fail(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<?>> handleValidation(MethodArgumentNotValidException exception) {
        ErrorDetail error = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fieldError -> ErrorDetail.of(
                ErrorCode.VALIDATION_ERROR,
                fieldError.getDefaultMessage(),
                fieldError.getField()
            ))
            .orElseGet(() -> ErrorDetail.of(ErrorCode.VALIDATION_ERROR, "Validation failed"));

        return ResponseEntity.badRequest().body(ApiResult.fail(error));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResult<?>> handleBadCredentials(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiResult.fail(ErrorDetail.of(
                ErrorCode.INVALID_CREDENTIALS,
                "Invalid username or password"
            ))
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<?>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiResult.fail(ErrorDetail.of(ErrorCode.FORBIDDEN, "Access denied"))
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResult<?>> handleOptimisticLock(OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResult.fail(ErrorDetail.of(
                ErrorCode.CONCURRENT_MODIFICATION,
                "The record was modified by another request. Please retry"
            ))
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResult<?>> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation", exception);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResult.fail(ErrorDetail.of(
                ErrorCode.CONFLICT,
                "The request conflicts with existing data"
            ))
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<?>> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResult.fail(ErrorDetail.of(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred"
            ))
        );
    }
}
