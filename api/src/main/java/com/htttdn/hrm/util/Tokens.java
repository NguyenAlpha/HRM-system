package com.htttdn.hrm.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Sinh token ngẫu nhiên và băm SHA-256 dùng chung; database chỉ lưu bản băm của token. */
public final class Tokens {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private Tokens() {
    }

    /** Token ngẫu nhiên {@code bytes} byte, mã hóa Base64 URL-safe không padding. */
    public static String random(int bytes) {
        byte[] value = new byte[bytes];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** SHA-256 của chuỗi UTF-8, dạng hex chữ thường. */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", exception);
        }
    }
}
