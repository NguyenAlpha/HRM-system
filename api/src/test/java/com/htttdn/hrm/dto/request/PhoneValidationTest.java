package com.htttdn.hrm.dto.request;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.htttdn.hrm.dto.request.employee.CreateEmployeeProfileRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateSelfProfileRequest;
import com.htttdn.hrm.dto.request.organization.CompanyProfileRequest;
import com.htttdn.hrm.dto.request.organization.CreateCompanyOwnerEmployeeRequest;
import com.htttdn.hrm.dto.request.worklocation.CreateWorkLocationRequest;
import com.htttdn.hrm.dto.request.worklocation.UpdateWorkLocationRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every request that takes a phone number accepts digits only, with an optional leading +. */
class PhoneValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static final List<Class<?>> PHONE_REQUESTS = List.of(
        CreateEmployeeProfileRequest.class,
        UpdateEmployeeRequest.class,
        UpdateSelfProfileRequest.class,
        CompanyProfileRequest.class,
        CreateCompanyOwnerEmployeeRequest.class,
        CreateWorkLocationRequest.class,
        UpdateWorkLocationRequest.class
    );

    static Stream<Arguments> validPhones() {
        return cases("0912345678", "02812345678", "+84912345678", "19001234", "");
    }

    static Stream<Arguments> invalidPhones() {
        return cases("09abc12345", "phone", "091 234 567", "091-234-567", "1234567", "++8491234567", "0912345678+");
    }

    @ParameterizedTest
    @MethodSource("validPhones")
    void phoneNumbersAreAccepted(Class<?> request, String phone) {
        assertTrue(VALIDATOR.validateValue(request, "phone", phone).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidPhones")
    void anythingButDigitsIsRejected(Class<?> request, String phone) {
        List<String> messages = VALIDATOR.validateValue(request, "phone", phone).stream()
            .map(ConstraintViolation::getMessage).toList();

        assertEquals(List.of(ValidationPatterns.PHONE_MESSAGE), messages);
    }

    private static Stream<Arguments> cases(String... phones) {
        return PHONE_REQUESTS.stream()
            .flatMap(request -> Stream.of(phones).map(phone -> Arguments.of(request, phone)));
    }
}
