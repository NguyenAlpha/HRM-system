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

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.exception.ResourceNotFoundException;

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

    /** The not deleted account of the employee with this code; codes compare case-insensitively. */
    @Query("""
        SELECT account
        FROM Account account
        WHERE LOWER(account.employee.employeeCode) = LOWER(:code) AND account.deletedAt IS NULL
        """)
    Optional<Account> findByEmployeeCode(@Param("code") String code);

    /**
     * The account a sign-in names: its username, else its email, else its employee code. Usernames
     * win, so a username equal to another person's employee code still signs in as its own account.
     */
    default Optional<Account> findBySignInName(String login) {
        return findByLogin(login).or(() -> findByEmployeeCode(login));
    }

    /** The employee's account that is not deleted, if any. */
    @Query("SELECT account FROM Account account WHERE account.employee.id = :employeeId AND account.deletedAt IS NULL")
    Optional<Account> findByEmployeeId(@Param("employeeId") Long employeeId);

    /** Whether the employee ever had an account, deleted ones included. */
    boolean existsByEmployeeId(Long employeeId);

    @Query("""
        SELECT account
        FROM Account account
        JOIN FETCH account.employee employee
        WHERE employee.id IN :employeeIds AND account.deletedAt IS NULL
        """)
    List<Account> findAllByEmployeeIds(@Param("employeeIds") List<Long> employeeIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM Account account WHERE account.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /*
     * "Tìm hoặc báo không thấy": default method của interface là code Java bình thường, Spring Data không
     * sinh truy vấn cho nó mà chạy nguyên thân hàm, nên có thể gọi các hàm sinh sẵn như findById.
     * Service gọi accountRepository.getRequired(id) thay vì tự viết findById(...).orElseThrow(...).
     * Quy ước tên của Spring Data: find... trả Optional, get... trả entity hoặc ném lỗi.
     */

    /** Tài khoản theo id (kể cả đã xóa mềm), không có thì 404 {@code ACCOUNT_NOT_FOUND}. */
    default Account getRequired(Long id) {
        return findById(id).orElseThrow(() -> notFound(id));
    }

    /** Như {@link #getRequired}, kèm khóa ghi ({@link #findByIdForUpdate}). */
    default Account getRequiredForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.ACCOUNT_NOT_FOUND, "Account not found: " + id);
    }
}
