package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.OrganizationUnit;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, Long> {

    Optional<OrganizationUnit> findByCodeAndDeletedAtIsNull(String code);

    Optional<OrganizationUnit> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    List<OrganizationUnit> findByParentUnitIdAndDeletedAtIsNull(Long parentUnitId);

    Page<OrganizationUnit> findByDeletedAtIsNull(Pageable pageable);
}
