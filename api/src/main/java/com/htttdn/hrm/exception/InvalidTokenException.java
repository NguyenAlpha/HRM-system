package com.htttdn.hrm.exception;

import com.htttdn.hrm.dto.response.common.ErrorCode;

public class InvalidTokenException extends UnauthorizedException {

    public InvalidTokenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
