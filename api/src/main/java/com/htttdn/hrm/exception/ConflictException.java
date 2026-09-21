package com.htttdn.hrm.exception;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import org.springframework.http.HttpStatus;

public class ConflictException extends AppException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.CONFLICT, message);
    }

    public ConflictException(ErrorCode errorCode, String message, String field) {
        super(errorCode, HttpStatus.CONFLICT, message, field);
    }
}
