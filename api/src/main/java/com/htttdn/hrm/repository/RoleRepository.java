package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.Role;

import jakarta.persistence.LockModeType;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCodeAndDeletedAtIsNull(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT role FROM Role role WHERE role.code = :code AND role.deletedAt IS NULL")
    Optional<Role> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCode(String code);

    Page<Role> findByDeletedAtIsNull(Pageable pageable);

    List<Role> findByDeletedAtIsNullOrderByIdAsc();
}
