package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.htttdn.hrm.entity.Account;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    /** The account list shows each account's employee, so load it in the same query. */
    @Override
    @EntityGraph(attributePaths = "employee")
    Page<Account> findAll(Specification<Account> specification, Pageable pageable);

    Optional<Account> findByUsername(String username);

    Optional<Account> findByEmail(String email);

    @Query("""
        SELECT account
        FROM Account account
        WHERE account.username = :login OR LOWER(account.email) = LOWER(:login)
        """)
    Optional<Account> findByLogin(@Param("login") String login);

    Optional<Account> findByEmployeeId(Long employeeId);

    @Query("""
        SELECT account
        FROM Account account
        JOIN FETCH account.employee employee
        WHERE employee.id IN :employeeIds
        """)
    List<Account> findAllByEmployeeIds(@Param("employeeIds") List<Long> employeeIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM Account account WHERE account.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
