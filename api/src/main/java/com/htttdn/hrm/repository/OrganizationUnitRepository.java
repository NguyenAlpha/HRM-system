package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.OrganizationUnit;

import jakarta.persistence.LockModeType;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, Long> {

    Optional<OrganizationUnit> findByCodeAndDeletedAtIsNull(String code);

    Optional<OrganizationUnit> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByParentUnitIdAndDeletedAtIsNull(Long parentUnitId);

    List<OrganizationUnit> findByParentUnitIdAndDeletedAtIsNull(Long parentUnitId);

    @EntityGraph(attributePaths = "parentUnit")
    List<OrganizationUnit> findByDeletedAtIsNullOrderByNameAsc();

    Page<OrganizationUnit> findByDeletedAtIsNull(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT unit FROM OrganizationUnit unit WHERE unit.id = :id AND unit.deletedAt IS NULL")
    Optional<OrganizationUnit> findByIdForUpdate(@Param("id") Long id);
}
