package com.htttdn.hrm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.PayslipItem;

public interface PayslipItemRepository extends JpaRepository<PayslipItem, Long> {

    List<PayslipItem> findByPayslipId(Long payslipId);

    void deleteByPayslipId(Long payslipId);
}
