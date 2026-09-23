package com.htttdn.hrm.exception;

import org.springframework.http.HttpStatus;

import com.htttdn.hrm.dto.response.common.ErrorCode;

public class UnauthorizedException extends AppException {

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.UNAUTHORIZED, message);
    }
}
