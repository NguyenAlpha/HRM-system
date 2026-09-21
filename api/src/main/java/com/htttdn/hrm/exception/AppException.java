package com.htttdn.hrm.exception;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Objects;

public abstract class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final String field;

    protected AppException(ErrorCode errorCode, HttpStatus status, String message) {
        this(errorCode, status, message, null);
    }

    protected AppException(ErrorCode errorCode, HttpStatus status, String message, String field) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.field = field;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }
}
