package com.htttdn.hrm.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.htttdn.hrm.config.seed.AdminSeedProperties;

/**
 * Cấu hình {@code jwt.*}: khóa ký, issuer và thời hạn của access/refresh token.
 * Cách Spring nạp record này: xem {@link AdminSeedProperties}.
 *
 * @param secret                      khóa HS256 dạng Base64, tối thiểu 32 byte sau khi decode;
 *                                    {@code ApplicationConfig} kiểm tra khi khởi động
 * @param issuer                      giá trị claim {@code iss} khi ký và khi xác minh token
 * @param accessTokenExpirationSeconds thời hạn access token, tính bằng giây
 * @param refreshTokenExpirationDays  thời hạn refresh token, tính bằng ngày
 */
@ConfigurationProperties("jwt")
public record JwtProperties(
    String secret,
    @DefaultValue("https://hrm.local") String issuer,
    @DefaultValue("900") long accessTokenExpirationSeconds,
    @DefaultValue("30") long refreshTokenExpirationDays
) { }
