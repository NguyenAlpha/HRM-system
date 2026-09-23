package com.htttdn.hrm.config;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import com.htttdn.hrm.security.AccountUserDetailsService;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.AuthService;

/**
 * Cấu hình các bean mã hóa mật khẩu và JWT dùng chung cho toàn bộ ứng dụng.
 *
 * <h3>Vai trò của từng bean</h3>
 * <ul>
 *   <li>{@link #passwordEncoder()} — BCrypt dùng để hash mật khẩu khi tạo/đổi mật khẩu
 *       và verify mật khẩu trong luồng đăng nhập.</li>
 *   <li>{@link #jwtEncoder(String)} — ký access token HS256 do {@link JwtService} phát hành.</li>
 *   <li>{@link #jwtDecoder(String, String)} — xác minh chữ ký, thời hạn và issuer của
 *       Bearer token trước khi request đi vào controller.</li>
 *   <li>{@link #jwtAuthenticationConverter()} — chuyển claims {@code roles} và
 *       {@code permissions} thành authorities trong Spring Security.</li>
 * </ul>
 *
 * <h3>Thành phần liên quan</h3>
 * <ul>
 *   <li>{@link AccountUserDetailsService} — load account trong luồng username/password login.</li>
 *   <li>{@link AuthService} — xác thực account và điều phối quá trình phát/đổi token.</li>
 *   <li>{@link JwtService} — tạo claims rồi gọi {@code JwtEncoder} để ký access token.</li>
 *   <li>{@link SecurityConfig} — gắn decoder và authentication converter vào
 *       OAuth2 Resource Server filter chain.</li>
 * </ul>
 */
@Configuration
public class ApplicationConfig {

    /**
     * BCryptPasswordEncoder với strength mặc định 10 rounds.
     *
     * <p>Bean này được dùng ở cả hai chiều: hash mật khẩu trước khi lưu DB và
     * verify mật khẩu khi {@code DaoAuthenticationProvider} xử lý đăng nhập.
     * Không lưu hoặc so sánh mật khẩu dạng rõ trong application code.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * NimbusJwtEncoder dùng HMAC-SHA256 để ký access token.
     *
     * <p>{@link JwtService} gọi bean này sau khi account đã được xác thực và role/permission
     * đã được tổng hợp. Khai báo thuật toán HS256 tường minh để encoder không chọn nhầm
     * signing algorithm khi tạo JWS.
     *
     * @param encodedSecret secret Base64, tối thiểu 32 bytes sau khi decode
     * @return encoder dùng chung để ký JWT
     */
    @Bean
    public JwtEncoder jwtEncoder(@Value("${jwt.secret}") String encodedSecret) {
        byte[] keyBytes = decodeAndValidateJwtSecret(encodedSecret);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtEncoder.withSecretKey(secretKey)
            .algorithm(MacAlgorithm.HS256)
            .build();
    }

    /**
     * NimbusJwtDecoder dùng HMAC-SHA256 để xác thực Bearer token mỗi request.
     *
     * <p>{@code BearerTokenAuthenticationFilter} tự động dùng bean này; application không
     * cần tự viết JWT filter. Validator mặc định kiểm tra format/thời hạn token, đồng thời
     * {@link JwtValidators#createDefaultWithIssuer(String)} bắt buộc claim {@code iss}
     * khớp với cấu hình {@code jwt.issuer}.
     *
     * @param encodedSecret cùng secret Base64 đã dùng để ký token
     * @param issuer issuer hợp lệ mà decoder chấp nhận
     * @return decoder đã cấu hình HS256 và issuer validation
     */
    @Bean
    public JwtDecoder jwtDecoder(
        @Value("${jwt.secret}") String encodedSecret,
        @Value("${jwt.issuer:https://hrm.local}") String issuer
    ) {
        byte[] keyBytes = decodeAndValidateJwtSecret(encodedSecret);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    /**
     * Chuyển {@link Jwt} đã validate thành {@code JwtAuthenticationToken}.
     *
     * <p>Role code được thêm prefix {@code ROLE_} theo convention của Spring Security;
     * ví dụ {@code SYSTEM_ADMIN} thành {@code ROLE_SYSTEM_ADMIN}. Permission code được
     * giữ nguyên, ví dụ {@code rbac.manage}, để dùng trực tiếp với {@code hasAuthority}.
     * Authentication sau đó được lưu trong {@code SecurityContext} cho toàn bộ request.
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    /**
     * Hợp nhất role và permission claims thành danh sách authorities không trùng lặp.
     *
     * <p>{@link LinkedHashSet} giữ thứ tự claims ổn định đồng thời loại bỏ authority trùng
     * khi một account nhận cùng quyền từ nhiều role assignment.
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<String> authorityNames = new LinkedHashSet<>();
        claimAsStringList(jwt, "roles").stream()
            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
            .forEach(authorityNames::add);
        authorityNames.addAll(claimAsStringList(jwt, "permissions"));

        return authorityNames.stream()
            .map(SimpleGrantedAuthority::new)
            .map(GrantedAuthority.class::cast)
            .toList();
    }

    /**
     * Đọc một JWT claim dạng array và chỉ nhận các phần tử string hợp lệ.
     *
     * <p>Token thiếu claim hoặc claim sai kiểu được xem như danh sách rỗng; chữ ký token
     * vẫn phải được decoder xác thực trước khi helper này được gọi.
     */
    private List<String> claimAsStringList(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        if (!(claim instanceof Collection<?> values)) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String stringValue) {
                result.add(stringValue);
            }
        }
        return result;
    }

    /**
     * Decode và kiểm tra {@code jwt.secret} ngay khi khởi tạo application context.
     *
     * <p>Fail-fast ngăn ứng dụng chạy với secret rỗng, sai Base64 hoặc ngắn hơn 256 bit.
     * Secret yếu cho phép kẻ tấn công brute-force key rồi tự ký JWT chứa role/permission
     * đặc quyền.
     *
     * @param encodedSecret secret lấy từ biến môi trường {@code JWT_SECRET}
     * @return key bytes đã decode và kiểm tra
     * @throws IllegalStateException nếu secret thiếu, sai Base64 hoặc ngắn hơn 32 bytes
     */
    private byte[] decodeAndValidateJwtSecret(String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalStateException(
                "jwt.secret is required; set JWT_SECRET to a Base64-encoded value of at least 32 bytes"
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("jwt.secret must be valid Base64", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must contain at least 32 bytes after Base64 decoding");
        }
        return keyBytes;
    }
}
