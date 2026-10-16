package com.htttdn.hrm.config;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.htttdn.hrm.dto.request.organization.CompanyProfileRequest;

import jakarta.validation.Validator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Request validation speaks Vietnamese, so the web can show the message to users unchanged. */
@SpringBootTest
class ValidationMessagesTest {

    @Autowired private Validator validator;

    @Test
    void standardConstraintsReportInVietnamese() {
        CompanyProfileRequest request = new CompanyProfileRequest(" ", "x".repeat(41), null, null, "not-an-email", null);

        Set<String> messages = validator.validate(request).stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.toSet());

        assertEquals(Set.of(
            "code: không được để trống",
            "name: phải có từ 0 đến 40 ký tự",
            "email: không đúng định dạng email"
        ), messages);
    }
}
