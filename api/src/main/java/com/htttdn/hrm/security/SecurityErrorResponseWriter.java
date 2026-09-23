package com.htttdn.hrm.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

import com.htttdn.hrm.dto.response.common.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;

final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    static void write(
        HttpServletResponse response,
        int status,
        ErrorCode errorCode,
        String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
            {"success":false,"data":null,"error":{"code":"%s","message":"%s","field":null}}"""
            .formatted(errorCode.name(), message));
    }
}
