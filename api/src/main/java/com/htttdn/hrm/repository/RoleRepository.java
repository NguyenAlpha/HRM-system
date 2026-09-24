package com.htttdn.hrm.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCode(String code);

    Page<Role> findByDeletedAtIsNull(Pageable pageable);
}
