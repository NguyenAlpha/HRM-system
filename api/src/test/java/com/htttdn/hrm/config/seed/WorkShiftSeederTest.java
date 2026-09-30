package com.htttdn.hrm.config.seed;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.repository.WorkShiftRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkShiftSeederTest {

    @Mock
    private WorkShiftRepository workShiftRepository;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void doesNothingWhenDisabled() {
        createSeeder(false).run(applicationArguments);

        verifyNoInteractions(workShiftRepository);
    }

    @Test
    void createsDefaultWorkShiftWhenMissing() {
        when(workShiftRepository.existsByCodeAndDeletedAtIsNull("OFFICE_DAY")).thenReturn(false);

        createSeeder(true).run(applicationArguments);

        ArgumentCaptor<WorkShift> shiftCaptor = ArgumentCaptor.forClass(WorkShift.class);
        verify(workShiftRepository).save(shiftCaptor.capture());
        WorkShift shift = shiftCaptor.getValue();
        assertEquals("OFFICE_DAY", shift.getCode());
        assertEquals("Ca hành chính", shift.getName());
        assertEquals(LocalTime.of(8, 0), shift.getStartTime());
        assertEquals(LocalTime.of(17, 0), shift.getEndTime());
        assertEquals(60, shift.getBreakMinutes());
        assertEquals(480, shift.getStandardWorkMinutes());
        assertEquals(15, shift.getGraceLateMinutes());
        assertFalse(shift.getCrossesMidnight());
        assertTrue(shift.getIsActive());
        assertNotNull(shift.getCreatedAt());
        assertNotNull(shift.getUpdatedAt());
        assertEquals(shift.getCreatedAt(), shift.getUpdatedAt());
        assertNull(shift.getDeletedAt());
    }

    @Test
    void retainsExistingWorkShift() {
        when(workShiftRepository.existsByCodeAndDeletedAtIsNull("OFFICE_DAY")).thenReturn(true);

        createSeeder(true).run(applicationArguments);

        verify(workShiftRepository, never()).save(any());
    }

    private WorkShiftSeeder createSeeder(boolean enabled) {
        return new WorkShiftSeeder(workShiftRepository, enabled);
    }
}
