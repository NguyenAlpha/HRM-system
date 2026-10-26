package com.htttdn.hrm.util;

/** Xử lý chuỗi dùng chung cho các service. */
public final class Strings {

    private Strings() {
    }

    /**
     * Chuẩn hóa ô nhập tùy chọn trước khi lưu: bỏ khoảng trắng hai đầu; null hoặc chỉ toàn khoảng trắng
     * → {@code null}, để database không chứa chuỗi rỗng.
     */
    public static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
