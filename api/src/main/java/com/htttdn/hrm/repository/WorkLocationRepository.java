package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.WorkLocation;

import jakarta.persistence.LockModeType;

public interface WorkLocationRepository extends JpaRepository<WorkLocation, Long> {

    Optional<WorkLocation> findByCodeAndDeletedAtIsNull(String code);

    Optional<WorkLocation> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByParentLocationIdAndDeletedAtIsNull(Long parentLocationId);

    List<WorkLocation> findByParentLocationIdAndDeletedAtIsNull(Long parentLocationId);

    @EntityGraph(attributePaths = "parentLocation")
    List<WorkLocation> findByDeletedAtIsNullOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT location FROM WorkLocation location WHERE location.id = :id AND location.deletedAt IS NULL")
    Optional<WorkLocation> findByIdForUpdate(@Param("id") Long id);
}
