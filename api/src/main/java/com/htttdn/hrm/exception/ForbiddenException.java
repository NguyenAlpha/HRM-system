package com.htttdn.hrm.exception;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import org.springframework.http.HttpStatus;

public class ForbiddenException extends AppException {

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.FORBIDDEN, message);
    }
}
