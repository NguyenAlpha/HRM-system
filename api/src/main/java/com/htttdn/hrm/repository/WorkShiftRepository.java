package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.WorkShift;

import jakarta.persistence.LockModeType;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    Optional<WorkShift> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    List<WorkShift> findByDeletedAtIsNullOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT workShift FROM WorkShift workShift "
        + "WHERE workShift.id = :id AND workShift.deletedAt IS NULL")
    Optional<WorkShift> findByIdForUpdate(@Param("id") Long id);
}
