package com.htttdn.hrm.exception;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.NOT_FOUND, message);
    }
}
