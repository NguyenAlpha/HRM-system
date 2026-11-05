package com.htttdn.hrm.dto.request;

/** Formats shared by several request DTOs. */
public final class ValidationPatterns {

    /**
     * An optional leading + and 8 to 11 digits: 0912345678, 02812345678, +84912345678, 19001234.
     * An empty string passes too, as the services already turn it into null.
     */
    public static final String PHONE = "^(\\+?[0-9]{8,11})?$";
    public static final String PHONE_MESSAGE = "chỉ gồm 8 đến 11 chữ số, có thể bắt đầu bằng dấu +";

    /**
     * A person's name: letters of any language (Vietnamese marks included) with spaces or . ' - between words, so
     * "Nguyễn Văn An" and "O'Brien" pass but "33333333333" or "An 2" do not. Surrounding spaces are allowed, as the
     * services trim; an empty string passes too, for optional fields the services turn into null.
     */
    public static final String PERSON_NAME = "^\\s*([\\p{L}\\p{M}]+([ .'-]+[\\p{L}\\p{M}]+)*\\.?)?\\s*$";
    public static final String PERSON_NAME_MESSAGE = "chỉ gồm chữ cái, khoảng trắng và các dấu . ' -";

    private ValidationPatterns() {
    }
}
