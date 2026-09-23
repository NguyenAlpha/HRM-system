package com.htttdn.hrm.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authenticationException
    ) throws IOException, ServletException {
        SecurityErrorResponseWriter.write(
            response,
            HttpServletResponse.SC_UNAUTHORIZED,
            ErrorCode.UNAUTHORIZED,
            "Authentication required. Provide a valid Bearer token"
        );
    }
}
