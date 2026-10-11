package com.htttdn.hrm.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.annotation.Nullable;

/**
 * Mô tả OpenAPI do springdoc sinh ra tại {@code /v3/api-docs} và Swagger UI tại
 * {@code /swagger-ui.html}.
 *
 * <p>Mọi endpoint mặc định yêu cầu Bearer JWT, khớp với {@link SecurityConfig}; bấm
 * "Authorize" trong Swagger UI rồi dán {@code accessToken} lấy từ {@code POST /api/auth/login}.
 * Frontend sinh TypeScript type từ bản chụp spec {@code apps/web/openapi.json}; đổi DTO thì
 * {@code OpenApiSnapshotTest} fail cho tới khi bản chụp và type được sinh lại.
 *
 * <h3>Quy ước null</h3>
 * <ul>
 *   <li>Response luôn serialize đủ mọi field (kể cả {@code null}), nên mọi property của schema
 *       dùng trong response được đánh dấu {@code required}.</li>
 *   <li>Field có thể {@code null} phải gắn {@code jakarta.annotation.Nullable}; spec khi đó
 *       thêm kiểu {@code null}. Field không gắn được coi là luôn có giá trị.</li>
 * </ul>
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(title = "HRM API", version = "v1"),
    // Fixed relative URL so the committed snapshot does not depend on host or port.
    servers = @Server(url = "/"),
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

    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    /**
     * Thêm kiểu {@code null} cho property gắn {@link Nullable}. swagger-core cũng nhận annotation
     * này cho field kiểu đơn giản; customizer cần cho DTO lồng nhau để property thành
     * {@code anyOf [$ref, null]}.
     */
    @Bean
    public PropertyCustomizer nullablePropertyCustomizer() {
        return (property, type) -> {
            boolean nullable = type.getCtxAnnotations() != null
                && Arrays.stream(type.getCtxAnnotations()).anyMatch(Nullable.class::isInstance);
            return nullable ? nullable(property) : property;
        };
    }

    /** Đánh dấu {@code required} mọi property của các schema xuất hiện trong response. */
    @Bean
    public OpenApiCustomizer responseFieldsRequiredCustomizer() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            // swagger-core tự xử lý @Nullable nhưng với DTO lồng nhau lại gắn kiểu null vào chính
            // schema dùng chung; tính nullable đã nằm ở anyOf của property nên gỡ khỏi component.
            schemas.values().stream()
                .filter(schema -> schema.getProperties() != null && schema.getTypes() != null)
                .forEach(schema -> {
                    schema.getTypes().remove("null");
                    schema.addType("object");
                });
            Deque<Schema<?>> pending = new ArrayDeque<>();
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                operation.getResponses().values().forEach(response -> {
                    if (response.getContent() != null) {
                        response.getContent().values().stream()
                            .map(MediaType::getSchema)
                            .forEach(pending::add);
                    }
                })
            ));

            Set<String> visited = new HashSet<>();
            while (!pending.isEmpty()) {
                Schema<?> schema = pending.pop();
                if (schema == null) {
                    continue;
                }
                if (schema.get$ref() != null) {
                    String name = schema.get$ref().substring(SCHEMA_REF_PREFIX.length());
                    if (visited.add(name) && schemas.get(name) != null) {
                        Schema<?> target = schemas.get(name);
                        if (target.getProperties() != null) {
                            target.setRequired(new ArrayList<>(target.getProperties().keySet()));
                        }
                        pending.push(target);
                    }
                    continue;
                }
                if (schema.getProperties() != null) {
                    schema.getProperties().values().forEach(pending::push);
                }
                if (schema.getItems() != null) {
                    pending.push(schema.getItems());
                }
                if (schema.getAdditionalProperties() instanceof Schema<?> values) {
                    pending.push(values);
                }
                if (schema.getAnyOf() != null) {
                    schema.getAnyOf().forEach(pending::push);
                }
            }
        };
    }

    /**
     * OpenAPI 3.1 biểu diễn null bằng kiểu {@code null}. Với DTO lồng nhau, swagger truyền vào
     * chính schema dùng chung trong components nên không được sửa nó; bọc $ref trong anyOf.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema nullable(Schema property) {
        String ref = property.get$ref() != null ? property.get$ref()
            : property.getName() != null ? SCHEMA_REF_PREFIX + property.getName()
            : null;
        if (ref != null) {
            Schema<?> nullType = new Schema<>();
            nullType.addType("null");
            return new Schema<>().anyOf(List.of(new Schema<>().$ref(ref), nullType));
        }
        if (property.getTypes() == null && property.getType() != null) {
            property.addType(property.getType());
        }
        property.addType("null");
        if (property.getEnum() != null && !property.getEnum().contains(null)) {
            property.getEnum().add(null);
        }
        return property;
    }
}
