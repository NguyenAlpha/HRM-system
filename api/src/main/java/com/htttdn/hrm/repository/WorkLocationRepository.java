package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.WorkLocation;

public interface WorkLocationRepository extends JpaRepository<WorkLocation, Long> {

    Optional<WorkLocation> findByCodeAndDeletedAtIsNull(String code);

    Optional<WorkLocation> findByIdAndDeletedAtIsNull(Long id);

    List<WorkLocation> findByParentLocationIdAndDeletedAtIsNull(Long parentLocationId);
}
