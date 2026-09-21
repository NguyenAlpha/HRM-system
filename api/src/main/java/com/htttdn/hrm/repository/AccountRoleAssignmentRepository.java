package com.htttdn.hrm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.AccountRoleAssignment;

public interface AccountRoleAssignmentRepository extends JpaRepository<AccountRoleAssignment, Long> {

    List<AccountRoleAssignment> findByAccountId(Long accountId);

    List<AccountRoleAssignment> findByAccountIdAndEffectiveToIsNull(Long accountId);
}
