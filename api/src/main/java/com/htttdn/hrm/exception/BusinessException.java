package com.htttdn.hrm.exception;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import org.springframework.http.HttpStatus;

public class BusinessException extends AppException {

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(ErrorCode errorCode, String message, String field) {
        super(errorCode, HttpStatus.BAD_REQUEST, message, field);
    }
}
