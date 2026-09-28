package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.JobPosition;

import jakarta.persistence.LockModeType;

public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {

    Optional<JobPosition> findByCodeAndDeletedAtIsNull(String code);

    Optional<JobPosition> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    List<JobPosition> findByDeletedAtIsNullOrderByTitleAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT jobPosition FROM JobPosition jobPosition "
        + "WHERE jobPosition.id = :id AND jobPosition.deletedAt IS NULL")
    Optional<JobPosition> findByIdForUpdate(@Param("id") Long id);
}
