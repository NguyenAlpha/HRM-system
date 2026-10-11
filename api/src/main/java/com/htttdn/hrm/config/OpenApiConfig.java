package com.htttdn.hrm.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Mô tả OpenAPI do springdoc sinh ra tại {@code /v3/api-docs} và Swagger UI tại
 * {@code /swagger-ui.html}.
 *
 * <p>Mọi endpoint mặc định yêu cầu Bearer JWT, khớp với {@link SecurityConfig}; bấm
 * "Authorize" trong Swagger UI rồi dán {@code accessToken} lấy từ {@code POST /api/auth/login}.
 * Frontend sinh TypeScript type từ spec này nên đổi DTO thì phải sinh lại type.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(title = "HRM API", version = "v1"),
    security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
)
@SecurityScheme(
    name = OpenApiConfig.BEARER_SCHEME,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";
}
