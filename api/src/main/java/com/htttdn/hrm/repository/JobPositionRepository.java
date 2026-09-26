package com.htttdn.hrm.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.JobPosition;

public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {

    Optional<JobPosition> findByCodeAndDeletedAtIsNull(String code);

    Optional<JobPosition> findByIdAndDeletedAtIsNull(Long id);
}
