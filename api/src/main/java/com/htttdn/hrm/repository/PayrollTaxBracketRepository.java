package com.htttdn.hrm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.PayrollTaxBracket;
import com.htttdn.hrm.entity.PayrollTaxBracketId;

public interface PayrollTaxBracketRepository extends JpaRepository<PayrollTaxBracket, PayrollTaxBracketId> {

    List<PayrollTaxBracket> findByIdTaxRuleIdOrderByIdLowerBound(Long taxRuleId);
}
