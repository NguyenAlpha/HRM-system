package com.htttdn.hrm.dto.request;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationPatternsTest {

    @ParameterizedTest
    @ValueSource(strings = {"Nguyễn Văn An", "Trần Thị-Bình", "O'Brien", "Lê Văn C.", "NGUYEN VAN AN", "  Phạm Hà  ", ""})
    void personNameAcceptsLettersWithSpacesAndNameMarks(String name) {
        assertTrue(name.matches(ValidationPatterns.PERSON_NAME), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"33333333333", "Nguyễn Văn 2", "An@gmail", "---", "Nguyễn  _An", "'An", "An!"})
    void personNameRejectsDigitsAndSymbols(String name) {
        assertFalse(name.matches(ValidationPatterns.PERSON_NAME), name);
    }
}
