package com.htttdn.hrm.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.WorkShift;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    Optional<WorkShift> findByIdAndDeletedAtIsNull(Long id);
}
